(ns enumeratio.core
  (:require [clojure.string :as str])
  #?(:cljs (:use-macros [enumeratio.core :only [defenum]])))

;;;;;;;;;;;;;;;;;;;;;;;;;
;; CORE IMPLEMENTATION ;;
;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol Enumerated
  (^:private interned-name [_])
  (^:private set-kw->element! [_ m])
  (validate! [_ x])
  (element [_ x])
  (element! [_ x]))

(defprotocol EnumerationElement
  (enumeration [_])
  (->keyword [_]))

(deftype Enumeration
  [name ^:unsynchronized-mutable kw->element]
  Enumerated
  (set-kw->element! [_ m] (set! kw->element m))
  (interned-name [_] name)
  (validate! [_ x]
    (or (contains? kw->element x)
        (throw (ex-info
                (str "Validation failed for " x ".")
                {:failed x
                 :allowed (-> kw->element keys set)}))))
  (element [_ x] (get kw->element x))
  (element! [_ x] (or (kw->element x)
                      (throw (ex-info "" {})))))

;;;;;;;;;;;;;;
;; PRINTING ;;
;;;;;;;;;;;;;;

#?(:cljs
   (extend-protocol IPrintWithWriter
     enumeratio.core.Enumeration
     (-pr-writer [v writer _]
       (write-all writer "#Enumeration[" (-> v interned-name str) "]")))
   :clj
   (defmethod print-method enumeratio.core.Enumeration
     [v ^java.io.Writer w]
     (.write w
             (format "#Enumeration[%s]"
                     (-> v interned-name)))))

#?(:clj
   (defmethod print-method enumeratio.core.EnumerationElement
     [v ^java.io.Writer w]
     (.write w
             (format "#EnumerationElement[%s:%s]"
                     (-> v enumeration (#'interned-name))
                     (-> v ->keyword name)))))

;; Protocols are NOT reified in cljs:
(defn cljs-enumeration-element-writer!
  [c]
  #?(:cljs
     (extend-protocol IPrintWithWriter
       c
       (-pr-writer [v writer _]
         (write-all writer
                    "#EnumerationElement["
                    (-> v enumeration (#'interned-name) str)
                    ":"
                    (-> v ->keyword name str)
                    "]")))))

;;;;;;;;;;;;;;;;;;
;; DATA READERS ;;
;;;;;;;;;;;;;;;;;;

(defn ->enumeration [[sym]] sym)
(defn ->element [[sym]] sym)

;;;;;;;;;;;;;;;;;;;
;; DEFENUM MACRO ;;
;;;;;;;;;;;;;;;;;;;
#?(:clj
   (defn- ->pascal-case
     [enum-name]
     (->> (str/split (name enum-name)
                     #"-")
          (mapcat str/capitalize)
          (apply str))))

#?(:clj
   (defn- wrap
     [impls]
     (for [[n args & body] impls]
       `(~n [this# ~@(rest args)]
         (let [~(first args) (->keyword this#)]
           ~@body)))))

#?(:clj
   (defn- process-opts+specs
     [opts+specs]
     (into {}
           (comp (partition-all 2)
                 (map (juxt ffirst
                            (comp wrap second))))
           (partition-by seq? opts+specs))))

#?(:clj
   (defn- resolve-to-str
     [sym]
     (let [resolution (resolve sym)]
       (if (class? resolution)
         #?(:clj (str resolution)
            :cljs resolution)
         (try (str (symbol resolution))
              (catch Throwable t
                (str sym)))))))

#?(:clj
   (defn- make-list
     [stuff]
     (->> stuff
          (map (partial format "  - %s"))
          (interpose "\n")
          (apply str))))

#?(:clj
   (defn- generate-enum-documentation
     [enum-name keywords opts+specs]
     (let [keywords (make-list keywords)
           interfaces (->> (process-opts+specs opts+specs)
                           (keys)
                           (map resolve-to-str)
                           (make-list))]
       (format "%s\n%s\n%s\n%s"
               "Enumeration of the following keywords:"
               keywords
               "Implements the following interfaces/protocols:"
               interfaces))))

#?(:clj
   (defn- generate-impls
     [enum-sym elt-sym opts+specs]
     (let [class->impls (process-opts+specs opts+specs)]
       (-> class->impls
           (assoc `EnumerationElement
                  `((~'enumeration [~'_] ~enum-sym)
                    (~'->keyword [~'_] ~elt-sym)))
           (->> (mapcat #(apply list (key %) (val %))))))))

#?(:clj
   (defn- ->element-name
     [enum-name keyword]
     (->> (name keyword)
          (format "%s:%s" enum-name)
          (symbol))))

#?(:clj
   (defn- generate-element-docs
     [kw docs enum-name type-name]
     (format "%s of the enumeration %s/%s.\nCorresponds to the keyword %s.\n\n%s"
             type-name
             *ns*
             enum-name
             kw
             docs)))

#?(:clj
   (defn- generate-element-defs
     [kw->docstring enum-name type-name]
     (for [[kw docs] kw->docstring
           :let [doc (generate-element-docs kw docs enum-name type-name)]]
       `(def ~(->element-name enum-name kw)
          ~doc
          (new ~type-name ~enum-name ~kw)))))

#?(:clj
   (defn- generate-kw->element
     [enum-name keywords]
     (into {}
           (map (juxt identity
                      (partial ->element-name enum-name)))
           keywords)))

#?(:clj
   (defn- generate-enum-for
     [enum-name]
     `(defn ~(symbol (str enum-name "-for"))
        [kw#]
        (element ~enum-name kw#))))

#?(:clj
   (defn- process-keywords
     [kws]
     (let [kw->docs (if (map? kws)
                      (do (assert (every? string? (vals kws)))
                          kws)
                      (zipmap kws (repeat "")))]
       (assert (apply distinct? (map name (keys kw->docs)))
               "Keywords for enumeration must have distinct names (regardless of namespace).")
       kw->docs)))

#?(:clj
   (defmacro ^{:arglists '([name [& keywords] & opts+specs])} defenum
     [enum-name keywords & opts+specs]
     (let [keyword->docstring (process-keywords keywords)
           type-name (symbol (->pascal-case (str enum-name "-element")))
           enumeration-sym (gensym 'enumeration)
           element-sym (gensym 'element)
           enum-documentation (generate-enum-documentation enum-name
                                                           (keys keyword->docstring)
                                                           opts+specs)
           opts+specs (generate-impls enumeration-sym
                                      element-sym
                                      opts+specs)
           enum-id (str *ns* "/" enum-name)]
       `(do (deftype ~type-name
                ~[enumeration-sym element-sym]
              ~@opts+specs)
            (def ~enum-name
              ~enum-documentation
              (Enumeration. ~enum-id nil))
            ~@(generate-element-defs keyword->docstring enum-name type-name)
            (#'set-kw->element! ~enum-name
                                ~(generate-kw->element enum-name (keys keyword->docstring)))
            (cljs-enumeration-element-writer! ~type-name)
            ~(generate-enum-for enum-name)))))
