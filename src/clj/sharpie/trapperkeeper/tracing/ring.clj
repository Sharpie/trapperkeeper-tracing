(ns sharpie.trapperkeeper.tracing.ring
  "Ring utilities for wrapping HTTP requests with OpenTracing contexts."
  (:require
    [ring.util.request :as ring-request]
    [sharpie.trapperkeeper.tracing :as tracing])
  (:import
    (io.opentracing.propagation Format$Builtin TextMapExtractAdapter)
    (io.opentracing.tag Tags)))


(defn wrap-with-tracing
  "Middleware to wrap requests with an OpenTracing context.

  This function takes a handler to be wrapped and a tracer instance
  and produces a new handler that extracts tracing information from
  request headers and then executes the wrapped handler inside of
  a trace context."
  [handler tracer]
  (fn [request]
    (let [headers (new TextMapExtractAdapter (:headers request))
          url (ring-request/request-url request)
          method (-> (:request-method request) name .toUpperCase)
          span-context (.extract tracer
                                 Format$Builtin/HTTP_HEADERS
                                 headers)]
      (binding [tracing/*tracer* tracer]
        ;; TODO: Set :id to be something like the Comidi route ID
        ;;       instead of just the HTTP method.
        (tracing/trace {:id method
                        :child-of span-context
                        ;; TODO: Add Tags/HTTP_STATUS from response.
                        ;; TODO: Add Tags/PEER_HOSTNAME if TLS certificate
                        ;;       is available as :ssl-client-cert.
                        :tags {(.getKey Tags/SPAN_KIND) Tags/SPAN_KIND_SERVER
                               (.getKey Tags/HTTP_URL) url
                               (.getKey Tags/HTTP_METHOD) method}}
                       (handler request))))))
