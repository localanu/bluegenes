(ns re-frame-boiler.components.listanalysis.events
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [com.rpl.specter.macros :refer [traverse]])
  (:require [re-frame.core :as re-frame :refer [reg-event-db reg-event-fx reg-fx dispatch]]
            [re-frame-boiler.db :as db]
            [imcljs.search :as search]
            [cljs.core.async :refer [put! chan <! >! timeout close!]]))

(reg-event-db
  :listanalysis/handle-results
  (fn [db [_ widget-name results]]
    (assoc-in db [:list-analysis :results (keyword widget-name)] results)))

(reg-fx
  :listanalysis/get-enrichment
  (fn [[widget-name results]]
    (go (dispatch [:listanalysis/handle-results widget-name (<! results)]))))

(reg-event-fx
  :listanalysis/run
  (fn [{db :db} [_ params]]
    (let [enrichment-chan
          (search/enrichment
            {:root "www.flymine.org/query"}
            params)]
      {:db                          db
       :listanalysis/get-enrichment [(:widget params) enrichment-chan]})))

(reg-fx
  :dispatch-many
  (fn [events]
    (doall (map dispatch events))))

(reg-event-fx
  :listanalysis/run-all
  (fn [{db :db}]
    {:db            (assoc-in db [:list-analysis :results] nil)
     :dispatch-many [[:listanalysis/run {:ids        (get-in db [:idresolver :saved (:temp (:panel-params db))])
                                         :maxp       0.05
                                         :widget     "pathway_enrichment"
                                         :correction "Holm-Bonferroni"}]
                     [:listanalysis/run {:ids        (get-in db [:idresolver :saved (:temp (:panel-params db))])
                                         :maxp       0.05
                                         :widget     "go_enrichment_for_gene"
                                         :correction "Holm-Bonferroni"}]
                     [:listanalysis/run {:ids        (get-in db [:idresolver :saved (:temp (:panel-params db))])
                                         :maxp       0.05
                                         :widget     "prot_dom_enrichment_for_gene"
                                         :correction "Holm-Bonferroni"}]
                     [:listanalysis/run {:ids        (get-in db [:idresolver :saved (:temp (:panel-params db))])
                                         :maxp       0.05
                                         :widget     "publication_enrichment"
                                         :correction "Holm-Bonferroni"}]
                     [:listanalysis/run {:ids        (get-in db [:idresolver :saved (:temp (:panel-params db))])
                                         :maxp       0.05
                                         :widget     "bdgp_enrichment"
                                         :correction "Holm-Bonferroni"}]
                     [:listanalysis/run {:ids        (get-in db [:idresolver :saved (:temp (:panel-params db))])
                                         :maxp       0.05
                                         :widget     "miranda_enrichment"
                                         :correction "Holm-Bonferroni"}]]}))