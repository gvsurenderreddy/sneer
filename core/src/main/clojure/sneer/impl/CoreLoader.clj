(ns sneer.impl.CoreLoader
  (:gen-class :implements [sneer.commons.Container$ComponentLoader])
  (:require sneer.admin
            sneer.contacts
            sneer.interfaces
            sneer.flux
            sneer.convos
            sneer.convo-summarization
            sneer.message-subs
            sneer.notifications
            sneer.sessions)
  (:import [sneer.commons Container Startup]))

(defn- start-components! [^Container container]
  (mapv #(.produce container %) [sneer.contacts/handle
                                 sneer.convos.Convos
                                 sneer.convos.Sessions])
  (reify Startup))

(defn -load [_this component-handle container]
  (condp = component-handle

    :lease
    (clojure.core.async/chan)

    Startup
    (start-components! container)

    sneer.contacts/handle
    (sneer.contacts/start! container)

    sneer.message-subs/handle
    (sneer.message-subs/start! container)

    sneer.admin.SneerAdmin
    (sneer.admin/reify-SneerAdmin container)

    sneer.flux.Dispatcher
    (sneer.flux/reify-Dispatcher container)

    sneer.convos.Convos
    (sneer.convos/reify-Convos container)

    sneer.convos.Sessions
    (sneer.sessions/reify-Sessions container)

    sneer.convos.Notifications
    (sneer.notifications/reify-Notifications container)

    sneer.interfaces.ConvoSummarization
    (sneer.convo-summarization/reify-ConvoSummarization container)))
