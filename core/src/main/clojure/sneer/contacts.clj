(ns sneer.contacts
  (:require
    [clojure.core.async :refer [chan <! >! alt! go close!]]
    [sneer.async :refer [go-trace state-machine tap-state peek-state! go-loop-trace wait-for! encode-nil sliding-chan close-with!]]
    [sneer.commons :refer [nvl]]
    [sneer.flux :refer [tap-actions response request action]]
    [sneer.keys :refer [from-hex]]
    [sneer.rx :refer [obs-tap]]
    [sneer.tuple-base-provider :refer :all]
    [sneer.tuple.protocols :refer [store-tuple query-with-history]])
  (:import
    [sneer.commons Container Clock]
    [sneer.flux Dispatcher]
    [sneer.admin SneerAdmin]
    [sneer.commons.exceptions FriendlyException]
    [java.util UUID]))

(def handle ::contacts)

(defn- produce [^Container con class]
  (.produce con class))

(defn from [container]
  (produce container handle))

(defn- puk [^SneerAdmin admin]
  (.. admin privateKey publicKey))

(defn- own-puk [container]
  (puk (produce container SneerAdmin)))

(defn- -store-tuple! [^Container container tuple]
  (let [admin (produce container SneerAdmin)
        own-puk (puk admin)
        tuple-base (tuple-base-of admin)
        defaults {"timestamp" (Clock/now)
                  "audience"  own-puk
                  "author"    own-puk}]
      (store-tuple tuple-base (merge defaults tuple))))

(defn- store-contact! [container nick contact-puk invite-code]
  (-store-tuple! container {"type"        "contact"
                            "payload"     nick
                            "party"       contact-puk
                            "invite-code" invite-code}))

(defn- -puk [id state]
  (get-in state [:id->contact id :puk]))

(defn- -invite-code [id state]
  (get-in state [:id->contact id :invite-code]))

(defn- -nickname [id state]
  (get-in state [:id->contact id :nick]))

(defn- -contact-id [puk state]
  (get-in state [:puk->id puk]))

(defn contact-list [state]
  (-> state :id->contact vals))

(defn puks [state]
  (-> state :puk->id keys set))

(defn tap [contacts & [ch]]
 (-> contacts :machine (tap-state ch)))

(defn tap-id [contacts id lease]
  (let [xcontact (comp (map (fn [state] (get-in state [:id->contact id])))
                       (filter some?))
        result (sliding-chan 1 xcontact)]
    (close-with! lease result)
    (tap contacts result)
    result))

(defn id->puk [contacts id]
  (go
    (let [lease (chan)
          contact (<! (tap-id contacts id lease))
          puk (contact :puk)]
      (close! lease)
      puk)))

(defn- assoc-puk [state puk id]
  (if puk
    (assoc-in state [:puk->id puk] id)
    state))

(defn- assoc-invite-code [state invite-code id]
  (if invite-code
    (assoc-in state [:invite-code->id invite-code] id)
    state))

(defn- handle-contact-delete [state tuple]
  (let [{puk "party" invite-code "invite-code"} tuple
        id (or (-contact-id puk state)
               (get-in state [:invite-code->id invite-code]))
        old-nick (-nickname id state)]
    (-> state
        (update-in [:id->contact] dissoc id)
        (update-in [:nick->id] dissoc old-nick)
        (update-in [:puk->id] dissoc puk)
        (update-in [:invite-code->id] dissoc (get-in state [:id->contact id :invite-code])))))

;; Contacts schema
#_{:id->contact {42 {:id 42
                     :nick "Neide"
                     :puk NeidePuk
                     :invite-code "ea4e35a3ea54e3"
                     :timestamp long}}
   :nick->id        {"Neide" 42}
   :puk->id         {NeidePuk "Neide"}
   :invite-code->id {"ea4e35a3ea54e3" 42}}
(defn- handle-contact [state own-puk tuple]
  (if-not (= (tuple "author") own-puk)
    state
    (let [{new-nick "payload"} tuple]
      (if (= new-nick "DELETED")
        (handle-contact-delete state tuple)
        (let [{puk "party" invite-code "invite-code" timestamp "timestamp"} tuple
              id (or (-contact-id puk state) (get-in state [:invite-code->id invite-code]) (tuple "id"))
              old-nick (-nickname id state)
              contact {:id id
                       :nick new-nick
                       :puk puk
                       :invite-code invite-code
                       :timestamp timestamp}]
          (-> state
              (assoc-in  [:id->contact id] contact)
              (update-in [:nick->id] dissoc old-nick)
              (assoc-in  [:nick->id new-nick] id)
              (assoc-puk puk id)
              (assoc-invite-code invite-code id)))))))

(defn- handle-push [state tuple]
;; {"type" "push" "audience" contact-puk "invite-code" invite-code-received}
  (let [invite-code (tuple "invite-code")
        contact-puk (tuple "author")
        inviter-puk (tuple "audience")]
    (if-some [id (get-in state [:invite-code->id invite-code])]
      (-> state
          (update-in [:id->contact id] assoc :puk contact-puk :timestamp (tuple "timestamp"))
          (update-in [:id->contact id] dissoc :invite-code)
          (assoc-in  [:puk->id contact-puk] id)
          (assoc-in  [:inviter-puk->id inviter-puk] id)
          (update-in [:invite-code->id] dissoc invite-code))
      state)))

(defn- handle-tuple [own-puk state tuple]
  (let [state (case (tuple "type")
                "contact" (handle-contact state own-puk tuple)
                "push"    (handle-push    state tuple)
                state)]
    (assoc state :last-id (tuple "id"))))

(defn- tuple-base [container]
  (tuple-base-of (produce container SneerAdmin)))

(defn- tuple-machine! [container]
  (let [old-tuples (chan 1)
        new-tuples (chan 1)
        lease (produce container :lease)
        own-puk (own-puk container)]
    (query-with-history (tuple-base container) {#_after-id #_starting-id} old-tuples new-tuples lease)
    (close-with! lease new-tuples)
    (state-machine (partial handle-tuple own-puk) {:last-id 0} old-tuples new-tuples)))

(defn- problem-with-nick [state nick]
  (cond
    (.isEmpty nick) "cannot be empty"
    (get-in state [:nick->id nick]) "already used"
    :else nil))

(defn- up-to-date? [state id]
  (>= (state :last-id) id))

(defn wait-for-store-contact! [container nick contact-puk invite-code contacts-states]
  (go-trace
    (let [tuple (<! (store-contact! container nick contact-puk invite-code))
          id (tuple "id")
          state (<! (wait-for! contacts-states #(up-to-date? % id)))]
      {:state state
       :id    id})))

(defn encode-invite [own-puk invite-code-suffix]
  (when invite-code-suffix
    (str (.toHex own-puk) "-" invite-code-suffix)))

(defn- decode-contact-puk [invite-code-received]
  (let [parts (.split invite-code-received "-")
        inviter-puk (from-hex (aget parts 0))
        suffix (aget parts 1)]
    [inviter-puk suffix]))

(defn handle-action! [container states state action]
  (go
    (case (action :type)

      "new-contact"
      (let [{:strs [nick]} action]
        (if-let [problem (problem-with-nick state nick)]
          (do
            (>! (response action) (FriendlyException. (str "Nickname " problem)))
            state)
          (let [invite-code (-> (UUID/randomUUID) .toString (.replaceAll "-" ""))
                result (<! (wait-for-store-contact! container nick nil invite-code states))]
            (>! (response action) (result :id))
            (result :state))))

      "delete-contact"
      (let [{:strs [contact-id]} action]
        (let [invite-code (-invite-code contact-id state)
              contact-puk (-puk contact-id state)]
          (let [result (<! (wait-for-store-contact! container "DELETED" contact-puk invite-code states))]
            (result :state))))

      "accept-invite"
      (let [{:strs [nick invite-code-received]} action]
        (if-let [problem (problem-with-nick state nick)]
          (do
            (>! (response action) (FriendlyException. (str "Nickname " problem)))
            state)
          (let [[inviter-puk suffix] (decode-contact-puk invite-code-received)
                _ (<! (-store-tuple! container {"type" "push" "audience" inviter-puk "invite-code" suffix}))
                result (<! (wait-for-store-contact! container nick inviter-puk nil states))]
            (>! (response action) (result :id))
            (result :state))))

      "find-convo"
      (let [{:strs [encoded-invite]} action
            [inviter-puk _suffix] (decode-contact-puk encoded-invite)]
        (>! (response action) (encode-nil (get-in state [:puk->id inviter-puk])))
        state)

      "problem-with-new-nickname"
      (let [{:strs [nick]} action]
        (>! (response action) (encode-nil (problem-with-nick state nick)))
        state)

      "set-nickname"
      (let [{:strs [contact-id new-nick]} action]
        (if-let [problem (problem-with-nick state new-nick)]
          (do
            (>! (response action) (FriendlyException. (str "Nickname " problem)))
            state)
          (do
            (close! (response action))
            (let [invite-code (-invite-code contact-id state)
                  contact-puk (-puk contact-id state)]
              (let [result (<! (wait-for-store-contact! container new-nick contact-puk invite-code states))]
                (result :state)))))) ;TODO: Change nickname even without puk, using id as "entity-id" in tuple.

      state)))

(defn- handle-actions! [container tuple-machine]
  (let [states (tap-state tuple-machine)
        actions (chan 1)]
    (tap-actions (produce container Dispatcher) actions)

    (go-loop-trace [state (<! states)]
      (when state
        (recur
          (alt!
            states
            ([new-state] new-state)

            actions
            ([action]
              (when action
                (<! (handle-action! container states state action))))))))))

(defn- dispatch [contacts request]
  (.request ^Dispatcher (contacts :dispatcher) request))

(defn problem-with-new-nickname [contacts nick]
  (dispatch contacts (request "problem-with-new-nickname" "nick" nick)))

(defn invite-code [contacts id]
  (obs-tap (contacts :machine) "invite-code tap" (map #(encode-nil (-invite-code id %)))))

(defn nickname [contacts id]
  (obs-tap (contacts :machine) "nickname tap" (map #(-nickname id %))))

(defn new-contact [contacts nick]
  (dispatch contacts (request "new-contact" "nick" nick)))

(defn accept-invite [contacts nick invite-code-received]
  (try
    (decode-contact-puk invite-code-received)
    (catch Throwable t
      (println "EXCEPTION")
      (.printStackTrace t System/out)))
  (dispatch contacts (request "accept-invite" "nick" nick "invite-code-received" invite-code-received)))

(defn find-convo [contacts encoded-invite]
  (try
    (decode-contact-puk encoded-invite)
    (catch Exception e
      (.printStackTrace e)))

  (dispatch contacts (request "find-convo" "encoded-invite" encoded-invite)))

(defn start! [container]
  (let [machine (tuple-machine! container)]
    (handle-actions! container machine)
    {:machine    machine
     :dispatcher (produce container Dispatcher)}))
