# Plumatic Schema Cheat Sheet

## Basic Setup

```clojure
(ns your.namespace
  (:require [schema.core :as s]))
```

## Basic Types

```clojure
;; Primitive types
s/Str          ; String
s/Int          ; Integer
s/Num          ; Number (int or float)
s/Bool         ; Boolean
s/Keyword      ; Keyword
s/Symbol       ; Symbol
s/Any          ; Any type

;; Collections
[s/Str]        ; Vector of strings
#{s/Int}       ; Set of integers
{s/Keyword s/Str} ; Map with keyword keys and string values
```

## Function Schemas with s/defn

```clojure
;; Basic function with type annotations
(s/defn add-numbers :- s/Int
  "Adds two numbers"
  [a :- s/Int
   b :- s/Int]
  (+ a b))

;; Function with complex return type
(s/defn find-user :- {:name s/Str :age s/Int}
  "Finds a user by ID"
  [user-id :- s/Int]
  {:name "John" :age 30})

;; Function with optional parameters
(s/defn greet :- s/Str
  "Greets someone"
  ([name :- s/Str]
   (str "Hello " name))
  ([name :- s/Str
    title :- s/Str]
   (str "Hello " title " " name)))
```

## Schema Definitions

```clojure
;; Define reusable schemas
(def User
  {:name s/Str
   :age s/Int
   :email s/Str})

;; Using s/defschema for better error messages
(s/defschema User
  "Schema for user data"
  {:name s/Str
   :age s/Int
   :email s/Str})

;; Optional keys
(def UserProfile
  {:name s/Str
   :age s/Int
   (s/optional-key :bio) s/Str      ; Optional bio field
   (s/optional-key :avatar) s/Str}) ; Optional avatar field

;; Required key (explicit, but redundant)
(def StrictUser
  {(s/required-key :name) s/Str
   :age s/Int})
```

## Advanced Schema Types

```clojure
;; Enums
(def Status (s/enum :active :inactive :pending))

;; Maybe (nullable)
(s/maybe s/Str)  ; String or nil

;; Either (union types)
(s/either s/Str s/Int)  ; String OR Integer

;; Conditional schemas
(s/conditional
  string? s/Str
  number? s/Int
  :else s/Any)

;; Predicate schemas
(s/pred pos?)           ; Positive number
(s/pred #(> % 18))      ; Custom predicate

;; Constrained schemas
(s/constrained s/Str #(> (count %) 3))  ; String longer than 3 chars
```

## Collection Schemas

```clojure
;; Vector schemas
[s/Str]                    ; Vector of strings
(s/vector s/Int)           ; Same as above, explicit

;; Set schemas
#{s/Keyword}               ; Set of keywords
(s/set s/Str)             ; Same as above, explicit

;; Map schemas
{s/Keyword s/Any}         ; Map with keyword keys
{s/Str s/Int}             ; Map with string keys and int values

;; Sequence schemas
(s/seq s/Int)             ; Sequence of integers
```

## Recursive Schemas

```clojure
;; Forward declaration for recursive types
(s/defschema TreeNode
  {:value s/Int
   :children [(s/recursive #'TreeNode)]}) ; Self-reference

;; Alternative syntax
(def TreeNode
  {:value s/Int
   :children [(s/recursive #'TreeNode)]})
```

## Validation

```clojure
;; Validate data against schema
(s/validate User {:name "John" :age 30 :email "john@example.com"})
;; Returns the data if valid, throws exception if invalid

;; Check if data is valid (returns boolean)
(s/check User {:name "John" :age "thirty"}) ; Returns error map
(s/check User {:name "John" :age 30})       ; Returns nil (valid)

;; Explain validation errors
(try
  (s/validate User {:name "John" :age "thirty"})
  (catch Exception e
    (println "Validation failed:" (.getMessage e))))
```

## Practical Examples from Clogs

```clojure
;; Query operators
(def Operator
  (s/enum :eq :ne :gt :gte :lt :lte :in :contains :starts-with :ends-with))

;; Condition schema
(def Condition
  {:field s/Keyword
   :operator Operator
   :value s/Any})

;; Recursive where clause
(s/defschema WhereClause
  (s/conditional
   #(contains? % :field) Condition
   #(contains? % :and) {:and [(s/recursive #'WhereClause)]}
   #(contains? % :or) {:or [(s/recursive #'WhereClause)]}
   #(contains? % :not) {:not (s/recursive #'WhereClause)}))

;; Query schema with optional keys
(def Query
  {(s/optional-key :find) [s/Keyword]
   (s/optional-key :where) WhereClause})

;; Validation result
(def ValidationResult
  {:valid? s/Bool
   (s/optional-key :errors) s/Any})

;; Function with schema
(s/defn validate-edn-query :- ValidationResult
  "Validates an EDN query"
  [edn-query :- s/Any]
  (try
    (s/validate Query edn-query)
    {:valid? true}
    (catch Exception e
      {:valid? false
       :errors (str "Invalid query: " (.getMessage e))})))
```

## Best Practices

1. **Use s/defschema for complex schemas**:
   ```clojure
   ;; Good
   (s/defschema User {:name s/Str :age s/Int})

   ;; Less readable for complex schemas
   (def User {:name s/Str :age s/Int})
   ```

2. **Always annotate function parameters and return types**:
   ```clojure
   (s/defn process-user :- ProcessedUser
     [user :- User
      options :- ProcessingOptions]
     ;; function body)
   ```

3. **Use meaningful schema names**:
   ```clojure
   ;; Good
   (s/defschema EmailAddress (s/pred #(re-matches #".+@.+" %)))

   ;; Less clear
   (def email-schema (s/pred #(re-matches #".+@.+" %)))
   ```

4. **Handle validation errors gracefully**:
   ```clojure
   (defn safe-validate [schema data]
     (try
       {:valid? true :data (s/validate schema data)}
       (catch Exception e
         {:valid? false :error (.getMessage e)})))
   ```

5. **Use s/maybe for nullable fields**:
   ```clojure
   {:name s/Str
    :middle-name (s/maybe s/Str)  ; Can be nil
    :age s/Int}
   ```

## Common Patterns

```clojure
;; API Response schema
(s/defschema ApiResponse
  {:status s/Keyword
   :data s/Any
   (s/optional-key :error) s/Str
   (s/optional-key :meta) {s/Keyword s/Any}})

;; Configuration schema
(s/defschema Config
  {:database {:host s/Str
              :port s/Int
              :name s/Str}
   :server {:port s/Int
            (s/optional-key :host) s/Str}
   (s/optional-key :debug) s/Bool})

;; Event schema with union types
(s/defschema Event
  (s/conditional
    #(= (:type %) :user-created) {:type (s/eq :user-created)
                                  :user User}
    #(= (:type %) :user-updated) {:type (s/eq :user-updated)
                                  :user User
                                  :changes {s/Keyword s/Any}}
    :else {:type s/Keyword
           :data s/Any}))
```