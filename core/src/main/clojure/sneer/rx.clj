(ns sneer.rx
  (:require
    [rx.lang.clojure.core :as rx]
    [rx.lang.clojure.interop :as interop])
  (:import [rx.subjects BehaviorSubject]
           [rx.schedulers Schedulers]
           [rx Observable]))

(defn on-subscribe [f]
  "Reifies a rx.Observable.OnSubscribe instance from a regular clojure function `f'."
  (reify rx.Observable$OnSubscribe
    (call [this subscriber] (f subscriber))))

(defn on-subscribe-for [^Observable observable]
  "Returns a rx.Observable.OnSubscribe instance that subscribes subscribers to `observable'."
  (on-subscribe
    (fn [^rx.Subscriber subscriber]
      (.add subscriber (.subscribe observable subscriber)))))

(defn subject* [^Observable observable ^rx.Observer observer]
  "Creates a rx.Subject from an `observable' part and an `observer' part."
  (let [subscriber (on-subscribe-for observable)]
    (sneer.rx.CompositeSubject. subscriber observer)))

(defn filter-by [criteria observable]
  "Filters an `observable' of maps by `criteria' represented as a map.
   Only maps containing all key/value pairs in criteria are kept."
  (let [ks (keys criteria)]
    (if ks
      (rx/filter #(= criteria (select-keys % ks)) observable)
      observable)))

(defn seq->observable [^java.lang.Iterable iterable]
  (Observable/from iterable))

(defn atom->observable [atom]
  (let [subject (BehaviorSubject/create @atom)]
    (add-watch atom (Object.) (fn [_key _ref _old-value new-value]
                                (rx/on-next subject new-value)))
    (.asObservable subject)))

(defn flatmapseq [^Observable o]
  (.flatMapIterable o (interop/fn [seq] seq)))

(defn observe-for-computation [^Observable o]
  (.observeOn o (Schedulers/computation)))

(defn observe-for-io [^Observable o]
  (.observeOn o (Schedulers/io)))

(defn subscribe-on-io
  ([^Observable o on-next-action]
   (rx/subscribe
    (subscribe-on-io o)
    on-next-action))
  ([^Observable o]
   (rx/subscribe-on (Schedulers/io) o)))

(defn shared-latest [^Observable o]
  "Returns a `rx.Observable' that publishes the latest value of the source sequence
   while sharing a single subscription as long as there are subscribers."
  (.. o (replay 1) refCount))

(defn latest [^Observable o]
  (doto (. o (replay 1))
    .connect))

(defn func-n [f]
  (reify rx.functions.FuncN
    (call [_ args] (f args))))

(defn combine-latest [f ^java.util.List os]
  (let [^rx.functions.FuncN fn (func-n f)]
    (Observable/combineLatest os fn)))

(defn switch-map [f ^rx.Observable o]
  (.switchMap o (interop/fn [x] (f x))))
