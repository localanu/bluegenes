(ns bluegenes.components.navbar.nav
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as reagent]
            [bluegenes.components.search.typeahead :as search]
            [bluegenes.components.tooltip.views :as tooltip]
            [accountant.core :refer [navigate!]]
            [oops.core :refer [ocall]]
            [bluegenes.components.progress_bar :as progress-bar]))

(defn mine-icon [mine]
  (let [icon (:icon mine)]
  [:svg.icon.logo {:class icon}
    [:use {:xlinkHref (str "#" icon) }]
   ]))


(defn settings []
  (let [current-mine (subscribe [:current-mine])]
  (fn []
    [:li.dropdown.mine-settings
     [:a.dropdown-toggle {:data-toggle "dropdown" :role "button"} [:svg.icon.icon-cog [:use {:xlinkHref "#icon-cog"}]]]
      (conj (into [:ul.dropdown-menu]
        (map (fn [[id details]]
          [:li {:on-click (fn [e] (dispatch [:set-active-mine (keyword id)]))
           :class (cond (= id (:id @current-mine)) "active")}
            [:a [mine-icon details]
                 (:name details)]]) @(subscribe [:mines])))
        [:li.special [:a {:on-click #(navigate! "/debug")} ">_ Developer"]])
])))

(defn save-data-tooltip []
  (let [label (reagent/atom nil)]
    (reagent/create-class
      {:component-did-mount
       (fn [e] (reset! label (:label (reagent/props e))))
       :reagent-render
       (fn [tooltip-data]
         [tooltip/main
          {:content [:div.form-inline
                     [:label "Name: "
                      [:input.form-control
                       {:autofocus   true
                        :type        "text"
                        :on-change   (fn [e] (reset! label (.. e -target -value)))
                        :placeholder @label}]]
                     [:button.btn "Save"]]
           :on-blur (fn []
                      (dispatch [:save-saved-data-tooltip (:id tooltip-data) @label]))}])})))

(defn active-mine-logo []
  [mine-icon @(subscribe [:current-mine])])

(defn main []
  (let [active-panel (subscribe [:active-panel])
        app-name     (subscribe [:name])
        short-name   (subscribe [:short-name])
        lists        (subscribe [:lists])
        ttip         (subscribe [:tooltip])
        current-mine (subscribe [:current-mine])
        panel-is     (fn [panel-key] (= @active-panel panel-key))]
    (fn []
      [:nav.navbar.navbar-default.navbar-fixed-top
       [:div.container-fluid
        [:ul.nav.navbar-nav.navbar-collapse.navigation
         [:li [:span.navbar-brand {:on-click #(navigate! "/")}
           [active-mine-logo]
           [:span.long-name (:name @current-mine)]]]
         [:li.homelink.larger-screen-only {:class (if (panel-is :home-panel) "active")} [:a {:on-click #(navigate! "/")} "Home"]]
         [:li {:class (if (panel-is :upload-panel) "active")} [:a {:on-click #(navigate! "/upload")}
          [:svg.icon.icon-upload.extra-tiny-screen [:use {:xlinkHref "#icon-upload"}]]
          [:span.larger-screen-only "Upload"]]]
         [:li {:class (if (panel-is :templates-panel) "active")} [:a {:on-click #(navigate! "/templates")} "Templates"]]

         ;;don't show region search for mines that have no example configured
         (cond (:regionsearch-example @current-mine)
           [:li {:class (if (panel-is :regions-panel) "active")} [:a {:on-click #(navigate! "/regions")} "Regions"]]
         )
         [:li {:class (if (panel-is :querybuilder-panel) "active")} [:a {:on-click #(navigate! "/querybuilder")} "Query\u00A0Builder"]]
         [:li {:class (if (panel-is :saved-data-panel) "active")} [:a {:on-click #(navigate! "/saved-data")} "Lists"[:span.larger-screen-only "\u00A0(" (apply + (map count (vals @lists))) ")"]]
          ;;example tooltip. Include as last child, probably with some conditional to display and an event handler for saving the name
          (if @ttip [save-data-tooltip @ttip])]]
        [:ul.nav.navbar-nav.navbar-right.buttons
         [:li.search [search/main]]
         (cond (not (panel-is :search-panel)) [:li.search-mini [:a {:on-click #(navigate! "/search")} [:svg.icon.icon-search [:use {:xlinkHref "#icon-search"}]]]])
         [:li.larger-screen-only [:a {:on-click #(navigate! "/help")} [:svg.icon.icon-question [:use {:xlinkHref "#icon-question"}]]]]
         ;;This may have worked at some point in the past. We need to res it.
        ; [user]
         [settings]]]
       [progress-bar/main]])))
