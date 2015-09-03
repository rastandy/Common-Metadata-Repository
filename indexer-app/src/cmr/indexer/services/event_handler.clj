(ns cmr.indexer.services.event-handler
  "Provides functions for subscribing to and handling events."
  (:require [cmr.common.lifecycle :as lifecycle]
            [cmr.indexer.config :as config]
            [cmr.indexer.services.index-service :as indexer]
            [cmr.message-queue.services.queue :as queue]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]))

(defmulti handle-ingest-event
  "Handle the various actions that can be requested via the indexing queue"
  (fn [context all-revisions-index? msg]
    (keyword (:action msg))))

(defmethod handle-ingest-event :default
  [_ _ _]
  ;; Default ignores the ingest event. There may be ingest events we don't care about.
  )

(defmethod handle-ingest-event :provider-collection-reindexing
  [context all-revisions-index? {:keys [provider-id]}]
  (indexer/reindex-provider-collections context [provider-id] all-revisions-index? false))

(defmethod handle-ingest-event :concept-update
  [context all-revisions-index? {:keys [concept-id revision-id]}]
  (indexer/index-concept-by-concept-id-revision-id
    context concept-id revision-id {:ignore-conflict? true
                                    :all-revisions-index? all-revisions-index?}))

(defmethod handle-ingest-event :concept-delete
  [context all-revisions-index? {:keys [concept-id revision-id]}]
  (indexer/delete-concept
    context concept-id revision-id {:ignore-conflict? true
                                    :all-revisions-index? all-revisions-index?}))

(defn handle-deleted-collection-revision-event
  "Handle the various actions that can be requested via the indexing queue"
  [context {:keys [concept-id revision-id]}]
  (indexer/force-delete-collection-revision context concept-id revision-id))

(defmethod handle-ingest-event :concept-revision-delete
  [context all-revisions-index? {:keys [concept-id revision-id]}]
  ;; We should never receive a message that's not for the all revisions index
  (when-not all-revisions-index?
    (errors/internal-error!
      (format (str "Received :concept-revision-delete event that wasn't for the all revisions "
                   "index.  concept-id: %s revision-id: %s")
              concept-id revision-id)))
  (indexer/force-delete-collection-revision context concept-id revision-id))

(defmethod handle-ingest-event :provider-delete
  [context _ {:keys [provider-id]}]
  (indexer/delete-provider context provider-id))

(defn subscribe-to-events
  "Subscribe to event messages on various queues"
  [context]
  (let [queue-broker (get-in context [:system :queue-broker])]
    (dotimes [n (config/index-queue-listener-count)]
      (queue/subscribe queue-broker
                       (config/index-queue-name)
                       #(handle-ingest-event context false %)))
    (dotimes [n (config/all-revisions-index-queue-listener-count)]
      (queue/subscribe queue-broker
                       (config/all-revisions-index-queue-name)
                       #(handle-ingest-event context true %)))))
