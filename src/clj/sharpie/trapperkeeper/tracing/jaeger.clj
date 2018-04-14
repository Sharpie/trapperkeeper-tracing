(ns sharpie.trapperkeeper.tracing.jaeger
  "Interface for jaegertracing.io.

  This namespace contains utilities for working with tracers from the
  Jaeger project. Jaeger provides a concrete implementation of operations
  defined by the OpenTracing API.

  See: https://jaegertracing.io"
  (:require
    [schema.core :as schema])
  (:import
    (io.jaegertracing Tracer$Builder)
    (io.jaegertracing.propagation CompositeCodec B3TextMapCodec TextMapCodec)
    (io.jaegertracing.reporters CompositeReporter LoggingReporter
                                NoopReporter Reporter RemoteReporter$Builder)
    (io.jaegertracing.samplers ConstSampler ProbabilisticSampler RateLimitingSampler)
    (io.jaegertracing.senders HttpSender$Builder UdpSender)
    (io.opentracing.propagation Format$Builtin)))


(schema/defschema CodecConfiguration
  "An enumeration of valid Jaeger codecs to configure a Tracer with.

  Jaeger codecs are responsible for injecting and extracting trace
  data from carriers. For example, when extracting a parent span from
  a map of HTTP headers, the codec would determine which HTTP header
  names to use."
  [(schema/enum "b3" "jaeger")])


(schema/defschema HttpSenderConfiguration
  "Configuration for sending spans as thrift data over HTTP."
  {:type (schema/eq "http")
   :endpoint schema/Str
   ;; NOTE: Can use either username/password or token. User/password will
   ;;       win if both are given.
   (schema/optional-key :username) schema/Str
   (schema/optional-key :password) schema/Str
   (schema/optional-key :token) schema/Str
   (schema/optional-key :max-packet-size) schema/Int})

(schema/defschema UdpSenderConfiguration
  "Configuration for sending spans as thrift data over UDP."
  {:type (schema/eq "udp")
   (schema/optional-key :host) schema/Str
   (schema/optional-key :port) schema/Int
   (schema/optional-key :max-packet-size) schema/Int})

(schema/defschema SenderConfiguration
  "Configuration for sending spans to a remote endpoint."
  (schema/conditional
    #(= "http" (:type %)) HttpSenderConfiguration
    #(= "udp" (:type %)) UdpSenderConfiguration))

(schema/defschema LoggingReporterConfiguration
  "Configuration for a simple reporter that sends span data to the logger."
  {:type (schema/eq "logging")})

(schema/defschema RemoteReporterConfiguration
  "Configuration for a reporter that sends span data to a Jaeger agent."
  {:type (schema/eq "remote")
   (schema/optional-key :sender) SenderConfiguration
   (schema/optional-key :max-queue-size) schema/Int
   (schema/optional-key :flush-interval) schema/Int})

(schema/defschema ReporterConfiguration
  [(schema/conditional
     #(= "logging" (:type %)) LoggingReporterConfiguration
     #(= "remote" (:type %)) RemoteReporterConfiguration)])


(schema/defschema ConstSamplerConfiguration
  "Hash structure for configuring a ConstSampler.

  ConstSampler instances are parameterized by a boolean which indicates
  whether every opertion is sampled."
  {:type (schema/eq "const")
   :param schema/Bool})

(schema/defschema ProbabilisticSamplerConfiguration
  "Hash structure for configuring a ProbabilisticSampler.

  ProbabilisticSampler instances are parameterized by a floating point
  value that indicates the probability a given operation will be sampled."
  {:type (schema/eq "probabilistic")
   :param java.lang.Double})

(schema/defschema RateLimitingSamplerConfiguration
  "Hash structure for configuring a RateLimitingSampler.

  RateLimitingSampler instances are parameterized by a floating point
  value that indicates the rate at which operations will be sampled."
  {:type (schema/eq "ratelimiting")
   :param java.lang.Double})

(schema/defschema SamplerConfiguration
  "Hash structure for configuring Jaeger Samplers."
  (schema/conditional
    #(= "const" (:type %)) ConstSamplerConfiguration
    #(= "probabilistic" (:type %)) ProbabilisticSamplerConfiguration
    #(= "ratelimiting" (:type %)) RateLimitingSamplerConfiguration))


(schema/defschema TracerConfiguration
  "Hash structure for configuring Jaeger Tracers.

  Patterned after the environment variables and properties used by the
  com.uber.jaeger.Configuration class."
  {:service-name schema/Str
   (schema/optional-key :codecs) CodecConfiguration
   (schema/optional-key :reporters) ReporterConfiguration
   (schema/optional-key :sampler) SamplerConfiguration})


(schema/defn ^:always-validate create-codecs
  "Creates map of codec instances from a CodecConfiguration."
  [config :- CodecConfiguration]
  (let [codecs (for [codec (set config)]
                 (case codec
                   ;; Generate pairs of Text and HTTP codecs.
                   "b3" [(new B3TextMapCodec) (new B3TextMapCodec)]
                   "jaeger" [(new TextMapCodec false) (new TextMapCodec true)]))
        arglists (apply (partial map vector) codecs)]
    (into {}
          (for [fmt [Format$Builtin/TEXT_MAP Format$Builtin/HTTP_HEADERS]
                args arglists]
            [fmt (new CompositeCodec args)]))))

(schema/defn ^:always-validate create-sender
  "Creates a Jaeger Sender from a SenderConfiguration."
  [config :- SenderConfiguration]
  (case (:type config)
    "http" (let [builder (new HttpSender$Builder (:endpoint config))
                 username (:username config)
                 password (:password config)
                 token (:token config)]
             (cond
               (and (not (nil? username)) (not (nil? password)))
                 (.withAuth builder username password)
               (not (nil? token))
                 (.withAuth builder token))
             (when-let [max-packet-size (:max-packet-size config)]
               (.withMaxPacketSize builder max-packet-size))

             (.build builder))

    "udp" (let [host (get config :host "")
                port (get config :port 0)
                max-packet-size (get config :max-packet-size 0)]
            (new UdpSender host port max-packet-size))))

(schema/defn ^:always-validate create-reporters
  "Creates a Jaeger Reporter from a ReporterConfiguration."
  [config :- ReporterConfiguration]
  (let [reporters (for [c config]
                    (case (:type c)
                      "logging" (new LoggingReporter)
                      "remote" (let [builder (new RemoteReporter$Builder)]
                                 (when-let [queue-size (:max-queue-size c)]
                                   (.withMaxQueueSize builder queue-size))
                                 (when-let [flush-interval (:flush-interval c)]
                                   (.withFlushInterval builder flush-interval))
                                 (when-let [sender-config (:sender c)]
                                   (.withSender builder (create-sender sender-config)))
                                 (.build builder))))]
    (if (> (count reporters) 1)
      (new CompositeReporter (into-array Reporter reporters))
      (first reporters))))


(schema/defn ^:always-validate create-sampler
  "Creates a Jaeger sampler from a SamplerConfiguration."
  [config :- SamplerConfiguration]
  (let [param (:param config)]
    (case (:type config)
      "const" (new ConstSampler param)
      "probabilistic" (new ProbabilisticSampler param)
      "ratelimiting" (new RateLimitingSampler param))))


(schema/defn ^:always-validate create-tracer-builder
  "Generate a new Jaeger Configuration instance from a hash of configuration.

  The input and behavior of this function mostly follows that of the
  `com.uber.jaeger.Configuration` class, except that configuration data
  is explicitly passed as a map instead of being read from System properties
  and environment variables.

  The `Tracer$Builder` instance also has a few defaults that are different
  from what a Configuration instance would produce:

    - codecs: This function selects B3 as the default instead of using
              the Jaeger format. B3 headers are understood by a wider
              range of tracing systems such as Zipkin and Brave. The
              TraceContext specification being developed by the W3C
              will become the default once published.

    - sampler: This function configures a `ConstSampler` that is
               disabled by default.

    - reporter: This function defaults to configuring a `NoopReporter`.

  The defaults for sampler and reporter allow the `Tracer$Builder` instances
  produced by this class to be used in test cases that may swap in different
  components. A key feature of the ConstSampler and NoopReporter is that
  neither class allocates any state on initialization, such as a connection
  to a remote system, that must be cleaned up with a call to `.close`."
  [config :- TracerConfiguration]
  (let [builder (new Tracer$Builder (:service-name config))]
    ;; Configure Codecs
    (doseq [[fmt codec] (create-codecs (get config :codecs ["b3"]))]
      (.registerInjector builder fmt codec)
      (.registerExtractor builder fmt codec))
    ;; Configure Sampling
    (if-let [sampler-config (:sampler config)]
      (.withSampler builder (create-sampler sampler-config))
      (.withSampler builder (new ConstSampler false)))
    ;; Configure Reporting
    (if-let [reporter-config (:reporters config)]
      (.withReporter builder (create-reporters reporter-config))
      (.withReporter builder (new NoopReporter)))))
