(ns redgenes.components.querybuilder.events
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [com.rpl.specter :refer [traverse]])
  (:require [re-frame.core :as re-frame :refer [reg-event-db reg-event-fx reg-fx dispatch subscribe]]
            [redgenes.db :as db]
            [redgenes.components.querybuilder.core :refer [build-query]]
            [cljs.core.async :refer [put! chan <! >! timeout close!]]
            [imcljs.search :as search]
            [imcljs.filters :as filters]
            [com.rpl.specter :as s]
            [clojure.string :as string]
            [clojure.zip :as zip]
            [cljs.reader :as reader]))

#_(def im-zipper (zip/zipper
                   (fn branch? [node] true)
                   (fn children [node]
                     (println "raw" (:collections node))
                     (let [child-classes (map (comp child-classes second) (:collections node))]
                       (.log js/console "returning" (select-keys model child-classes))
                       (select-keys model child-classes)))
                   (fn make-node [node children]
                     (println "makde node called")
                     (assoc node :collections children))
                   (-> db :assets :model :Gene)))

(defn child-classes [c]
  (keyword (:referencedType c)))

(defn nth-child [z idx]
  (nth (iterate zip/right z) idx))

(reg-event-db
  :query-builder/reset-query
  (fn [db [_ count]]
    (-> db
      (assoc-in [:query-builder :query] nil)
      (assoc-in [:query-builder :count] nil))))

(defn next-letter [letter]
  (let [alphabet (into [] "ABCDEFGHIJKLMNOPQRSTUVWXYZ")]
    (first (rest (drop-while (fn [n] (not= n letter)) alphabet)))))

(reg-event-fx
  :query-builder/add-constraint
  (fn [{db :db} [_ constraint]]
    {:db
      (let [used-codes (last (sort (map :q/code (get-in db [:query-builder :query :q/where]))))
            next-code  (if (nil? used-codes) "A" (next-letter used-codes))]
         (-> db
             (update-in [:query-builder :query :q/where]
               (fn [where] (conj where (merge constraint {:q/code next-code}))))
             (assoc-in [:query-builder :constraint] nil)))
     :dispatch [:query-builder/run-query]}))

(reg-event-db
  :query-builder/handle-count
  (fn [db [_ count]]
    (-> db
        (assoc-in [:query-builder :count] count)
        (assoc-in [:query-builder :counting?] false))))

(reg-fx
  :query-builder/run-query
  (fn [query]
    (go (dispatch [:query-builder/handle-count (<! (search/raw-query-rows
                                       {:root @(subscribe [:mine-url])}
                                       query
                                       {:format "count"}))]))))

(reg-event-fx
  :query-builder/run-query
  (fn [{db :db}]
     (let [query-data (-> db :query-builder :query)]
       {:db                      (assoc-in db [:query-builder :counting?] true)
        :query-builder/run-query (build-query query-data)})))

(reg-event-db
  :query-builder/make-tree
  (fn [db]
    (let [model (-> db :assets :model)]
      #_(assoc-in db [:query-builder :query]
                  {:from   "Gene"
                   :select [["Gene" "alleles" "alleleClass"]
                            ["Gene" "secondaryIdentifier"]
                            ["Gene" "primaryIdentifier"]]})
      db)))

(reg-event-db
  :query-builder/done-query!?
  (fn [db]
    (update-in db [:query-builder :queried?] not)))

(reg-event-fx
  :query-builder/remove-select
  (fn [{db :db} [_ path]]
    {:db       (update-in db [:query-builder :query :q/select]
                          (fn [views]
                            (remove #(= % path) views)))
     :dispatch :query-builder/run-query}))

(reg-event-fx
  :query-builder/remove-constraint
  (fn [{db :db} [_ path]]
    {:db       (update-in db [:query-builder :query :q/where]
                          (fn [wheres]
                            (remove #(= % path) wheres)))
     :dispatch [:query-builder/run-query]}))

(reg-event-db
  :query-builder/add-filter
  (fn [db [_ path]]
    (assoc-in db [:query-builder :constraint] path)))

(reg-event-db
  :query-builder/set-logic
  (fn [db [_ expression]]
    (assoc-in db [:query-builder :query :q/logic] expression)))

(reg-event-db
  :query-builder/set-query
  (fn [db [_ query-str]]
    (assoc-in db [:query-builder :query] (reader/read-string query-str))))

(reg-event-fx
  :query-builder/add-view
  (fn [{db :db} [_ path-vec]]
    {:db       (update-in db [:query-builder :query :q/select]
                          (fn [views]
                            (if (some #(= % path-vec) views)
                              (remove #(= % path-vec) views)
                              (conj views path-vec))))
     :dispatch [:query-builder/run-query]}))
