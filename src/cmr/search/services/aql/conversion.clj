(ns cmr.search.services.aql.conversion
  "Contains functions for parsing and converting aql to query conditions"
  (:require [clojure.string :as s]
            [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.common.services.errors :as errors]
            [cmr.search.models.query :as qm]
            [cmr.search.services.parameters.conversion :as p]))

(def aql-elem->converter-attrs
  "A mapping of aql element names to query condition types based on concept-type"
  {:collection {:dataCenterId {:name :provider-id :type :string}
                :shortName {:name :short-name :type :string}
                :versionId {:name :version-id :type :string}
                :CampaignShortName {:name :project :type :string}
                :dataSetId {:name :entry-title :type :string}
                :ECHOLastUpdate {:name :update-since :type :datetime}
                :onlineOnly {:name :downloadable :type :boolean}
                :ECHOCollectionID {:name :concept-id :type :string}
                :processingLevel {:name :processing-level-id :type :string}
                :sensorName {:name :sensor :type :string}
                :sourceName {:name :platform :type :string}
                :instrumentShortName {:name :instrument :type :string}
                ;; TODO spatial
                :spatial {:name :spatial :type :spatial}
                :temporal {:name :temporal :type :temporal}
                :difEntryId {:name :dif-entry-id :type :dif-entry-id}
                :entry-id {:name :entry-id :type :string}
                :associated-difs {:name :associated-difs :type :string}
                :scienceKeywords {:name :science-keywords :type :string}
                :TwoDCoordinateSystemName {:name :two-d-coordinate-system-name :type :string}}
   :granule {:dataCenterId {:name :provider-id :type :collection-query}
             :GranuleUR {:name :granule-ur :type :string}
             :collectionShortName {:name :short-name :type :collection-query}
             :collectionVersionId {:name :version-id :type :collection-query}
             :browseOnly {:name :browsable :type :string}
             :CampaignShortName {:name :project :type :string}
             :cloudCover {:name :cloud-cover :type :num-range}
             :dataSetId {:name :entry-title :type :collection-query}
             :dayNightFlag {:name :day-night :type :string}
             :ECHOLastUpdate {:name :update-since :type :datetime}
             :onlineOnly {:name :downloadable :type :boolean}
             :ECHOCollectionID {:name :collection-concept-id :type :string}
             :ECHOGranuleID {:name :concept-id :type :string}
             :ProducerGranuleID {:name :producer-gran-id :type :string}
             :sensorName {:name :sensor :type :string}
             :sourceName {:name :platform :type :string}
             :instrumentShortName {:name :instrument :type :string}
             ;; TODO spatial
             :spatial {:name :spatial :type :spatial}
             :temporal {:name :temporal :type :temporal}
             :additionalAttributes {:name :attribute :type :attribute}
             ;; orbit-number is not in UMM yet
             :orbitNumber {:name :orbit-number :type :string}
             ;; :equator-cross-longitude is not in UMM yet
             :equatorCrossingLongitude {:name :equator-cross-longitude :type :num-range}
             ;; :equator-cross-date is not in UMM yet
             :equatorCrossingDate {:name :equator-cross-date :type :datetime}
             :TwoDCoordinateSystemName {:name :two-d-coordinate-system-name :type :string}}})

(defn- elem-name->type
  "Returns the query condition type based on the given concept-type and aql element name."
  [concept-type elem-name]
  (get-in aql-elem->converter-attrs [concept-type elem-name :type]))

(defn- elem-name->condition-key
  "Returns the query condition key name based on the given concept-type and aql element name."
  [concept-type elem-name]
  (get-in aql-elem->converter-attrs [concept-type elem-name :name]))

(defn- string-value-elem->condition
  "Converts a string value element to query condition"
  ([concept-type key elem]
   (string-value-elem->condition concept-type key elem false))
  ([concept-type key elem pattern?]
   (let [value (first (:content elem))
         case-insensitive (get-in elem [:attrs :caseInsensitive])
         case-sensitive? (if (and case-insensitive (= "N" (s/upper-case case-insensitive))) true false)
         case-sensitive? (if (some? (p/always-case-sensitive key)) true case-sensitive?)]
     (if pattern?
       (let [new-value (-> value
                           (s/replace #"([^\\])(%)" "$1*")
                           (s/replace #"([^\\])(_)" "$1?")
                           (s/replace #"^%(.*)" "*$1")
                           (s/replace #"^_(.*)" "?$1"))]
         (qm/string-condition key new-value case-sensitive? pattern?))
       (qm/string-condition key value case-sensitive? pattern?)))))

(defn- string-pattern-elem->condition
  "Converts a string value element to query condition"
  [concept-type key elem]
  (string-value-elem->condition concept-type key elem true))

(defmulti element->condition
  "Converts a aql element into a condition"
  (fn [concept-type elem]
    (elem-name->type concept-type (:tag elem))))

(defn string-element->condition
  ([concept-type element]
   (let [condition-key (elem-name->condition-key concept-type (:tag element))]
     (string-element->condition concept-type condition-key (first (:content element)))))
  ([concept-type key element]
   (let [elem-type (:tag element)]
     (case elem-type
       :value (string-value-elem->condition concept-type key element)
       :textPattern (string-pattern-elem->condition concept-type key element)
       ;; list and patternList can be processed the same way below
       (qm/or-conds
         (map (partial string-element->condition concept-type key) (:content element)))))))

(defmethod element->condition :string
  [concept-type element]
  (string-element->condition concept-type element))

(defmethod element->condition :dif-entry-id
  [concept-type element]
  (qm/or-conds
    [(element->condition concept-type (assoc element :tag :entry-id))
     (element->condition concept-type (assoc element :tag :associated-difs))]))

(def aql-query-type->concept-type
  "Mapping of AQL query type to search concept type"
  {"collections" :collection
   "granules" :granule})

(defn validate-aql
  "Validates the XML against the AQL schema."
  [xml]
  ;; TODO: add validation later.
  )

(defn- condition-elem-group->conditions
  "Convert a collectionCondition|granuleCondition element group to conditions"
  [concept-type condition-elems]
  (map (partial element->condition concept-type) condition-elems))

(defn- get-concept-type
  "Parse and returns the concept-type from the parsed AQL xml struct"
  [xml-struct]
  (let [aql-query-type (:value (cx/attrs-at-path xml-struct [:for]))]
    (aql-query-type->concept-type aql-query-type)))

(defn- xml-struct->data-center-condition
  "Parse and returns the data-center-condition from the parsed AQL xml struct"
  [concept-type xml-struct]
  (when (nil? (cx/content-at-path xml-struct [:dataCenterId :all]))
    (element->condition concept-type (cx/element-at-path xml-struct [:dataCenterId]))))

(defn- xml-struct->where-conditions
  "Parse and returns the where conditions from the parsed AQL xml struct"
  [concept-type xml-struct]
  (let [condition-key (if (= :collection concept-type) :collectionCondition :granuleCondition)
        condition-groups (cx/contents-at-path xml-struct [:where condition-key])]
    (mapcat (partial condition-elem-group->conditions concept-type) condition-groups)))

(defn- xml-struct->query-condition
  "Parse and returns the query condition from the parsed AQL xml struct"
  [concept-type xml-struct]
  (let [data-center-condition (xml-struct->data-center-condition concept-type xml-struct)
        where-conditions (xml-struct->where-conditions concept-type xml-struct)
        conditions (if data-center-condition
                     (cons data-center-condition where-conditions)
                     where-conditions)]
    (when (seq conditions) (qm/and-conds conditions))))

(defn aql->query
  "Converts aql into a query model."
  [params aql]
  (validate-aql aql)
  (let [page-size (Integer. (get params :page-size qm/default-page-size))
        page-num (Integer. (get params :page-num qm/default-page-num))
        pretty (get params :pretty false)
        result-format (:result-format params)
        xml-struct (x/parse-str aql)
        concept-type (get-concept-type xml-struct)
        condition (xml-struct->query-condition concept-type xml-struct)]
    (qm/query {:concept-type concept-type
               :page-size page-size
               :page-num page-num
               :pretty pretty
               :condition condition
               :result-format result-format})))

