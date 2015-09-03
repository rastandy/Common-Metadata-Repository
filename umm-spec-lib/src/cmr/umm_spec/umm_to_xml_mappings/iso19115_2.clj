(ns cmr.umm-spec.umm-to-xml-mappings.iso19115-2
  "Defines mappings from UMM records into ISO19115-2 XML."
  (:require [cmr.umm-spec.umm-to-xml-mappings.iso-util :refer [gen-id]]
            [cmr.umm-spec.umm-to-xml-mappings.dsl :refer :all]
            [cmr.umm-spec.simple-xpath :as xp]))

(def iso19115-2-xml-namespaces
  {:xmlns:xs "http://www.w3.org/2001/XMLSchema"
   :xmlns:gmx "http://www.isotc211.org/2005/gmx"
   :xmlns:gss "http://www.isotc211.org/2005/gss"
   :xmlns:gco "http://www.isotc211.org/2005/gco"
   :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
   :xmlns:gmd "http://www.isotc211.org/2005/gmd"
   :xmlns:gmi "http://www.isotc211.org/2005/gmi"
   :xmlns:gml "http://www.opengis.net/gml/3.2"
   :xmlns:xlink "http://www.w3.org/1999/xlink"
   :xmlns:eos "http://earthdata.nasa.gov/schema/eos"
   :xmlns:srv "http://www.isotc211.org/2005/srv"
   :xmlns:gts "http://www.isotc211.org/2005/gts"
   :xmlns:swe "http://schemas.opengis.net/sweCommon/2.0/"
   :xmlns:gsr "http://www.isotc211.org/2005/gsr"})

(defn- date-mapping
  "Returns the date element mapping for the given name and date value in string format."
  [date-name value]
  [:gmd:date
   [:gmd:CI_Date
    [:gmd:date
     [:gco:DateTime value]]
    [:gmd:dateType
     [:gmd:CI_DateTypeCode {:codeList "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#CI_DateTypeCode"
                            :codeListValue date-name} date-name]]]])

(defn- make-instrument-title
  "Returns an ISO title string from a XPath context containing an instrument record."
  [{[instrument] :context}]
  (str (:ShortName instrument)
       ">"
       (:LongName instrument)))

(comment
  ;; The following two functions are unused, pending some answers on IDs in ISO XML platforms and
  ;; instruments.
  
  (defn- unique-id
    "Returns a unique ID string for the first value in the XPath context."
    [{[x] :context}]
    (format "%x" (hash x)))

  (defn- unique-id-ref-from
    [xpath]
    (fn [xpath-context]
      (str "#" (unique-id (xp/evaluate xpath-context xpath))))))

(def attribute-type-code-list
  "http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeTypeCode")

(def attribute-data-type-code-list
  "http://earthdata.nasa.gov/metadata/resources/Codelists.xml#EOS_AdditionalAttributeDataTypeCode")

(defn- make-characteristics-mapping
  "Returns a UMM->XML mapping for the Characteristics under the current XPath context, with the type
  argument used for the EOS_AdditionalAttributeTypeCode codeListValue attribute value and content."
  [type]
  [:eos:otherProperty
   [:gco:Record
    [:eos:AdditionalAttributes
     (for-each "Characteristics"
       [:eos:AdditionalAttribute
        [:eos:reference
         [:eos:EOS_AdditionalAttributeDescription
          [:eos:type
           [:eos:EOS_AdditionalAttributeTypeCode {:codeList attribute-type-code-list
                                                  :codeListValue type}
            type]]
          [:eos:name
           (char-string-from "Name")]
          [:eos:description
           (char-string-from "Description")]
          [:eos:dataType
           [:eos:EOS_AdditionalAttributeDataTypeCode {:codeList attribute-data-type-code-list
                                                      :codeListValue (xpath "DataType")}
            (xpath "DataType")]]
          [:eos:parameterUnitsOfMeasure
           (char-string-from "Unit")]]]
        [:eos:value
         (char-string-from "Value")]])]]])

(defn- generate-descriptive-keywords
  "Returns content generator instruction for the descriptive keywords field. We create this function
  because we don't want to generate the parent elements when there are no TemporalKeywords."
  [xpath-context]
  (when-let [temporal-keywords (-> xpath-context :context first :TemporalKeywords seq)]
    (vec (concat
           [:gmd:MD_Keywords]

           (for [temporal-keyword temporal-keywords]
             [:gmd:keyword [:gco:CharacterString temporal-keyword]])

           [[:gmd:type
             [:gmd:MD_KeywordTypeCode
              {:codeList "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_KeywordTypeCode"
               :codeListValue "temporal"} "temporal"]]
            [:gmd:thesaurusName {:gco:nilReason "unknown"}]]))))

(def umm-c-to-iso19115-2-xml
  [:gmi:MI_Metadata
   iso19115-2-xml-namespaces
   [:gmd:fileIdentifier (char-string-from "/EntryTitle")]
   [:gmd:language (char-string "eng")]
   [:gmd:characterSet
    [:gmd:MD_CharacterSetCode {:codeList "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_CharacterSetCode"
                               :codeListValue "utf8"} "utf8"]]
   [:gmd:hierarchyLevel
    [:gmd:MD_ScopeCode {:codeList "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_ScopeCode"
                        :codeListValue "series"} "series"]]
   [:gmd:contact {:gco:nilReason "missing"}]
   [:gmd:dateStamp
    [:gco:DateTime "2014-08-25T15:25:44.641-04:00"]]
   [:gmd:metadataStandardName (char-string "ISO 19115-2 Geographic Information - Metadata Part 2 Extensions for imagery and gridded data")]
   [:gmd:metadataStandardVersion (char-string "ISO 19115-2:2009(E)")]
   [:gmd:identificationInfo
    [:gmd:MD_DataIdentification
     [:gmd:citation
      [:gmd:CI_Citation
       [:gmd:title (char-string-from "/EntryTitle")]
       (date-mapping "revision" "2000-12-31T19:00:00-05:00")
       (date-mapping "creation" "2000-12-31T19:00:00-05:00")
       [:gmd:identifier
        [:gmd:MD_Identifier
         [:gmd:code (char-string-from "/EntryId")]
         [:gmd:version (char-string-from "/Version")]]]]]
     [:gmd:abstract (char-string-from "/Abstract")]
     [:gmd:purpose {:gco:nilReason "missing"} (char-string-from "/Purpose")]
     [:gmd:descriptiveKeywords generate-descriptive-keywords]
     [:gmd:resourceConstraints
      [:gmd:MD_LegalConstraints
       [:gmd:useLimitation (char-string-from "/UseConstraints")]
       [:gmd:useLimitation
        [:gco:CharacterString (concat-parts "Restriction Comment:" (xpath "/AccessConstraints/Description"))]]
       [:gmd:otherConstraints
        [:gco:CharacterString (concat-parts "Restriction Flag:" (xpath "/AccessConstraints/Value"))]]]]
     [:gmd:language (char-string-from "/DataLanguage")]
     [:gmd:extent
      [:gmd:EX_Extent
       (for-each "/TemporalExtents/RangeDateTimes"
         [:gmd:temporalElement
          [:gmd:EX_TemporalExtent
           [:gmd:extent
            [:gml:TimePeriod {:gml:id gen-id}
             [:gml:beginPosition (xpath "BeginningDateTime")]
             [:gml:endPosition (xpath "EndingDateTime")]]]]])
       (for-each "/TemporalExtents/SingleDateTimes"
         [:gmd:temporalElement
          [:gmd:EX_TemporalExtent
           [:gmd:extent
            [:gml:TimeInstant {:gml:id gen-id}
             [:gml:timePosition (xpath ".")]]]]])]]]]

   [:gmd:dataQualityInfo
    [:gmd:DQ_DataQuality
     [:gmd:scope
      [:gmd:DQ_Scope
       [:gmd:level
        [:gmd:MD_ScopeCode
         {:codeList "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#MD_ScopeCode"
          :codeListValue "series"}
         "series"]]]]
     [:gmd:report
      [:gmd:DQ_AccuracyOfATimeMeasurement
       [:gmd:measureIdentification
        [:gmd:MD_Identifier
         [:gmd:code
          (char-string "PrecisionOfSeconds")]]]
       [:gmd:result
        [:gmd:DQ_QuantitativeResult
         [:gmd:valueUnit ""]
         [:gmd:value
          [:gco:Record {:xsi:type "gco:Real_PropertyType"}
           [:gco:Real (xpath "/TemporalExtents[1]/PrecisionOfSeconds")]]]]]]]]]
   [:gmi:acquisitionInformation
    [:gmi:MI_AcquisitionInformation
     (for-each "/Platforms"
       [:gmi:platform
        [:eos:EOS_Platform ;; TODO {:id unique-id}
         [:gmi:identifier
          [:gmd:MD_Identifier
           [:gmd:code
            (char-string-from "ShortName")]
           [:gmd:description
            (char-string-from "LongName")]]]
         [:gmi:description (char-string-from "Type")]
         (for-each "Instruments"
           [:gmi:instrument
            [:eos:EOS_Instrument ;; TODO {:id unique-id}
             [:gmi:citation
              [:gmd:CI_Citation
               [:gmd:title
                [:gco:CharacterString make-instrument-title]]
               [:gmd:date {:gco:nilReason "unknown"}]]]
             [:gmi:identifier
              [:gmd:MD_Identifier
               [:gmd:code
                (char-string-from "ShortName")]
               [:gmd:description
                (char-string-from "LongName")]]]
             [:gmi:type
              (char-string-from "Technique")]
             [:gmi:description {:gco:nilReason "missing"}]
             ;; TODO this:
             ;; [:gmi:mountedOn {:xlink:href (unique-id-ref-from "..")}]
             [:eos:otherPropertyType
              [:gco:RecordType {:xlink:href "http://earthdata.nasa.gov/metadata/schema/eos/1.0/eos.xsd#xpointer(//element[@name='AdditionalAttributes'])"}
               "Echo Additional Attributes"]]
             ;; TODO see if this can be abstracted out since it is mostly duplicated in the next
             ;; section
             (make-characteristics-mapping "instrumentInformation")
             
             ]])

         ;; Characteristics
         (for-each "Characteristics[1]"
           [:eos:otherPropertyType
            [:gco:RecordType {:xlink:href "http://earthdata.nasa.gov/metadata/schema/eos/1.0/eos.xsd#xpointer(//element[@name='AdditionalAttributes'])"}
             "Echo Additional Attributes"]])
         [:eos:otherProperty
          [:gco:Record
           [:eos:AdditionalAttributes
            (for-each "Characteristics"
              [:eos:AdditionalAttribute
               [:eos:reference
                [:eos:EOS_AdditionalAttributeDescription
                 [:eos:type
                  [:eos:EOS_AdditionalAttributeTypeCode {:codeList attribute-type-code-list
                                                         :codeListValue "platformInformation"}
                   "platformInformation"]]
                 [:eos:name
                  (char-string-from "Name")]
                 [:eos:description
                  (char-string-from "Description")]
                 [:eos:dataType
                  [:eos:EOS_AdditionalAttributeDataTypeCode {:codeList attribute-data-type-code-list
                                                             :codeListValue (xpath "DataType")}
                   (xpath "DataType")]]
                 [:eos:parameterUnitsOfMeasure
                  (char-string-from "Unit")]]]
               [:eos:value
                (char-string-from "Value")]])]]]

         ]])]]])