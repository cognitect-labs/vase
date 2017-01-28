# Adding Vase to a Pedestal project

## Welcome

This guide will help you add Vase to an existing Pedestal
project. Vase creates pedestal routes based on an application
description. These routes can be concatenated to your application's
routes.

## What You Will Learn

After reading this guide, you will be able to:

- Add the Vase dependency to a Pedestal service
- Add one or more Vase APIs

## Guide Assumptions

The guide assumes you created your Pedestal project with the template:

```
lein new pedestal-service my-new-service
```

## Step By Step

### Step 1: Add the Vase dependency

In your `project.clj`, add the Vase dependency information:

```
  :dependencies [,,,
                 [com.cognitect/pedestal.vase "0.9.0-SNAPSHOT"]
                 ,,,
                ]
```

### Step 2: Update `service.clj`

#### Add the new required namespaces to your `service.clj` ns declaration

```clojure
(ns my-new-service.service
  (:require ;...
            [com.cognitect.vase]))
```

#### Add Vase information to the service map

```clojure
(def service-map
   {:env :prod

    ,,,

    ;; Add these lines
    ::route-set routes
    ::vase/api-root "/api"
    ::vase/spec-resources ["vase-api-descriptor.edn"]
    ,,,
   }
```

If you want more than one descriptor file, just add their names to the
`::vase/spec-resources` vector.

### Step 3: Update `server.clj`

Add this function to register specs and transact schema:

```clojure
(defn activate-vase
  ([base-routes api-root spec-paths]
   (activate-vase base-routes api-root spec-paths vase/load-edn-resource))
  ([base-routes api-root spec-paths vase-load-fn]
   (let [vase-specs (mapv vase-load-fn spec-paths)]
     (when (seq vase-specs)
       (vase/ensure-schema vase-specs)
       (vase/specs vase-specs))
     {::routes (if (empty? vase-specs)
                 base-routes
                 (into base-routes (vase/routes api-root vase-specs)))
      ::specs vase-specs})))
```

Add this function to update a Pedestal service map with Vase info:

```clojure
(defn vase-service
  "Optionally given a default service map and any number of string paths
  to Vase API Specifications,
  Return a Pedestal Service Map with all Vase APIs parsed, ensured, and activated."
  ([]
   (vase-service service/service))
  ([service-map]
   (vase-service service-map vase/load-edn-resource))
  ([service-map vase-load-fn]
   (merge {:env :prod
           ::server/routes (::routes (activate-vase
                                       (::service/route-set service-map)
                                       (::vase/api-root service-map)
                                       (::vase/spec-resources service-map)
                                       vase-load-fn))}
          service-map)))
```

Update `run-dev` to account for loading resources from the filesystem instead of the classpath:

```clojure
(defn run-dev
  "The entry-point for 'lein run-dev'"
  [& args]
  (println "\nCreating your [DEV] server...")
  (-> service/service ;; start with production configuration
      (merge {:env :dev
              ;; do not block thread that starts web server
              ::server/join? false
              ;; Routes can be a function that resolve routes,
              ;;  we can use this to set the routes to be reloadable
              ::server/routes #(route/expand-routes
                                 (::routes (activate-vase (deref #'service/routes)
                                                          (::vase/api-root service/service)
                                                          (mapv (fn [res-str]
                                                                  (str "resources/" res-str))
                                                                (::vase/spec-resources service/service))
                                                          vase/load-edn-file)))
              ;; all origins are allowed in dev mode
              ::server/allowed-origins {:creds true :allowed-origins (constantly true)}})
      ;; Wire up interceptor chains
      server/default-interceptors
      server/dev-interceptors
      server/create-server
      server/start))
```

### Step 4: Create services

Your Pedestal application is now Vase-enabled. You can now start
populating `resources/vase-api-descriptor.edn` (or whatever you choose
to call it) with your API definition.

## Wrapping Up

You've seen how to:

- Add Vase to an existing Pedestal service project
- Activate a Vase API in the service map
- Add one or more API descriptors

The next step is to begin [defining APIs](./your_first_api.md)
