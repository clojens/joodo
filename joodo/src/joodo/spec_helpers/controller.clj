(ns ^{:doc "This namespace is comprised of functions that work with the Speclj testing framework to make testing controller logic easy."}
  joodo.spec-helpers.controller
  (:require [speclj.core :refer :all ]
            [chee.datetime :refer [minutes-from-now]]
            [chee.util :refer [->options]]
            [joodo.middleware.request :refer [*request*]]
            [joodo.views :refer [*view-context* render-template render-html template-stream]]))

(declare ^{:dynamic true
           :doc "Holds all of the loaded routes. Can be altered for testing
  purposes by using the with-routes function"}
  *routes*)

(defn with-routes
  "Binds the supplied routes to the *routes* var so that tests can use those
  routes as resources when using do-get, do-post, or request function calls."
  [routes]
  (around [it]
    (binding [*routes* routes]
      (it))))

(defn request
  "Simulates a request. Expects the first argument to be the method for the
  request, the second argument to point to a resource, and the remaining
  arguments to be parameters."
  [method resource & extras]
  (let [request {:request-method method :uri resource}
        request (if (seq extras) (apply assoc request extras) request)]
    (binding [*request* request]
      (*routes* request))))

(defn do-get
  "Simulates a get request. Expects the first argument to point to a resource.
  All additional arguments get bound to *view-context* as parameters."
  [resource & extras]
  (apply request :get resource extras))

(defn do-post
  "Simulates a post request. Expects the first argument to point to a resource.
  All additional arguments get bound to *view-context* as parameters."
  [resource & extras]
  (apply request :post resource extras))

(def ^{:doc "Holds the relative location and name of the file that was most
  recently rendered."}
  rendered-template (atom nil))

(def ^{:doc "Holds the html that was most recently rendered."}
  rendered-html (atom nil))

(def ^{:doc "Holds the parameters that were passed to the view file for the most
  recent rendering."}
  rendered-context (atom nil))

(defn mock-render-template
  "Sets rendered-template to the template provided in the first arguement
  and sets rendered-context to the addtional arguments provided. Then
  returns the name of the template provided."
  [template & args]
  (reset! rendered-template template)
  (reset! rendered-context (merge *view-context* (->options args)))
  (str template))

(defn strict-mock-render-template
  "Wrapper for mock-render-template that firsts checks to see if the template exists."
  [template & args]
  (if-let [stream (template-stream template)]
    (let [result (apply mock-render-template template args)]
      (.close stream)
      result)
    (throw (Exception. (format "(mock rendering) Template missing: %s (template-root: %s)" template (:template-root *view-context*))))))

(defn mock-render-html
  "Sets rendered-html to the html provided in the first argument and sets
  rendered-context to the additional arguments provided. Then returns the
  html that was mock-rendered."
  [html & args]
  (reset! rendered-html html)
  (reset! rendered-context (merge *view-context* (->options args)))
  html)

(defn with-mock-rendering
  "Binds render-template to mock-render-template and render-hmtl to
  mock-render-html.
  Options:
    :strict true|false - will check that templates exist and fail if not
    All other options are passed through to the *view-context*"
  [& args]
  (let [options (->options args)]
    [(before (reset! rendered-template nil))
     (before (reset! rendered-context nil))
     (around [it]
       (with-redefs [render-template (if (:strict options) strict-mock-render-template mock-render-template)
                     render-html mock-render-html]
         (binding [*view-context* (merge *view-context* (dissoc options :strict))]
           (it))))]))

  (defmacro should-redirect-to
    "Tests that a request redirects to a given location. Expects the first
argument to be a map representing the request (Such maps can be produced
by the do-get and do-post functions). Expects the second argument to be
a string representing the expected location."
    [response location]
    `(do
       (should= 302 (:status ~response))
       (should= ~location ((:headers ~response) "Location"))))
