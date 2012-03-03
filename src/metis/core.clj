(ns metis.core
  (:use
    [metis.validations :only [validation-factory]]
    [metis.util]
    [clojure.set :only [union]]))

(defn -should-run? [options attr context]
  (let [{:keys [allow-nil allow-blank on]
         :or {allow-nil false
              allow-blank false
              on [:create :update]}} options
        on (flatten [on])]
    (not (or
      (not (includes? on context))
      (and allow-nil (nil? attr))
      (and allow-blank (blank? attr))))))

(defn -run-validation
  ([record attr validation-name validation-args]
    (-run-validation record attr validation-name validation-args :create))
  ([record attr validation-name validation-args context]
    (let [error (when (-should-run? validation-args (attr record) context) ((validation-factory validation-name) record attr validation-args))]
      (when error
        (if (:message validation-args) (:message validation-args) error)))))

(defn -remove-nil [coll]
  (filter #(not (nil? %)) coll))

(defn -run-validations [record attr validations]
  (-remove-nil
    (for [[validation-name validation-args] validations]
      (-run-validation record attr validation-name validation-args))))

(defn -merge-errors [errors]
  (apply merge {} errors))

(defn -remove-empty-values [map-to-filter]
  (select-keys map-to-filter (for [entry map-to-filter :when (not (empty? (val entry)))] (key entry))))

(defn validate [record validations]
  (-remove-empty-values
    (-merge-errors
      (for [validation validations]
        (let [attr (key validation)
              attr-vals (val validation)]
          {attr (-run-validations record attr attr-vals)})))))

(defn -parse-attributes [attributes]
  (flatten [attributes]))

(defn -parse-validations [validations]
  (let [validations (flatten [validations])]
    (loop [validations validations ret []]
      (if (empty? validations)
        ret
        (let [cur (first validations)
              next (second validations)]
          (cond
            (map? next)
            (recur (rest (rest validations)) (conj ret [cur next]))
            (keyword? next)
            (recur (rest validations) (conj ret [cur {}]))
            (nil? next)
            (recur [] (conj ret [cur {}]))))))))

(defn -parse
  ([attrs validation args] [(-parse-attributes attrs) (-parse-validations [validation args])])
  ([attrs validations] [(-parse-attributes attrs) (-parse-validations validations)]))

(defn -merge-validations [validations]
  (apply merge-with union {} validations))

(defn -expand-validation [validation]
  (let [[attributes validations] (apply -parse validation)]
    (-merge-validations
      (for [attr attributes validation validations]
        {attr #{validation}}))))

(defn -expand-validations [validations]
  (-merge-validations (map -expand-validation validations)))

(defmacro defvalidator [name & validations]
  (let [validations (-expand-validations validations)]
    `(defn ~name [record#]
      (validate record# ~validations))))