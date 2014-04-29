(ns cmr.search.data.query-to-elastic
  "Defines protocols and functions to map from a query model to elastic search query"
  (:require [clojurewerkz.elastisch.query :as q]
            [clojure.string :as s]
            [cmr.search.models.query :as qm]
            [cmr.search.data.datetime-helper :as h]))

(def field-mappings
  "A map of fields in the query to the field name in elastic. Field names are excluded from this
  map if the query field name matches the field name in elastic search."
  {:collection {:entry_title :entry-title
                :provider :provider-id
                :short_name :short-name
                :version :version-id
                :granule_ur :granule-ur
                :collection_concept_id :collection-concept-id
                :project :project-sn
                :two_d_coordinate_system_name :two-d-coord-name}
   :granule {:entry_title :entry-title
             :provider :provider-id
             :short_name :short-name
             :version :version-id
             :granule_ur :granule-ur
             :collection_concept_id :collection-concept-id
             :project :project-ref}})

(defn query-field->elastic-field
  "Returns the elastic field name for the equivalent query field name."
  [concept-type field]
  (get field-mappings field field))

(defprotocol ConditionToElastic
  "Defines a function to map from a query to elastic search query"
  (condition->elastic
    [concept-type condition]
    "Converts a query model condition into the equivalent elastic search filter"))

(defn query->elastic
  "Converts a query model into an elastic search query"
  [query]
  (let [{:keys [concept-type condition]} query]
    {:filtered {:query (q/match-all)
                :filter (condition->elastic concept-type condition)}}))

(extend-protocol ConditionToElastic
  cmr.search.models.query.ConditionGroup
  (condition->elastic
    [concept-type {:keys [operation conditions]}]
    ;; TODO Performance Improvement: We should order the conditions within and/ors.
    {operation {:filters (map (apply condition->elastic concept-type) conditions)}})

  cmr.search.models.query.StringCondition
  (condition->elastic
    [concept-type {:keys [field value case-sensitive? pattern?]}]
    (let [field (query-field->elastic-field concept-type field)
          field (if case-sensitive? field (str (name field) ".lowercase"))
          value (if case-sensitive? value (s/lower-case value))]
      (if pattern?
        {:query {:wildcard {field value}}}
        {:term {field value}})))

  cmr.search.models.query.ExistCondition
  (condition->elastic
    [_ {:keys [field]}]
    {:exists {:field field}})

  cmr.search.models.query.MissingCondition
  (condition->elastic
    [_ {:keys [field]}]
    {:missing {:field field}})

  cmr.search.models.query.DateRangeCondition
  (condition->elastic
    [_ {:keys [field start-date end-date]}]
    (let [from-value (if start-date (h/utc-time->elastic-time start-date) h/earliest-echo-start-date)
          value {:from from-value}
          value (if end-date (assoc value :to (h/utc-time->elastic-time end-date)) value)]
      {:range { field value }}))

  cmr.search.models.query.MatchAllCondition
  (condition->elastic
    [_ _]
    {:match_all {}})

  cmr.search.models.query.MatchNoneCondition
  (condition->elastic
    [_ _]
    {:term {:match_none "none"}}))
