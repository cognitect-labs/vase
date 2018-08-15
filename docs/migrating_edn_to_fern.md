# Migrating EDN to Fern

EDN is just data. Fern is just data. There's no reason a human should
have to do mechanical conversions.

The `dev` directory includes a `tools.clj` file with a conversion
utility.

To load up your EDN file and run it through the converter:

```clojure
(use 'tools)
(in-ns 'tools)
(edn-file->fern-file "old-file.edn" "new-file.fern")
```

Feel free to dig into tools.clj to see how you can use the conversion
machinery.

# After Conversion

Mechanical translation can take care of getting the meaning across, but it can't format things in your preferred style. You'll want to look at two things in your Fern output:

1. Order of definitions
2. Linebreaks and alignments

## Order of Definitions

The Fern output will come out in roughly the order that the EDN map
was enumerated. That bears little resemblance to either the input
format or any sensible reading of the definitions. You'll want to
spend some time making the new Fern file readable to other
human. Depending on your taste, you might move the `vase/service`
definition to the top, then chain down the page. Or vice versa, with
the biggest aggregates toward the bottom.

Either way, this is something you'll need to do by hand.

## Linebreaks and Alignment

Sadly, the pretty-printer has very different opinions about where to
put line breaks. You'll probably want to adjust the spacing yourself.

For example, running the conversion on
`test/resources/test_descriptor.edn` gives this for a part of the
output:

```clojure
 example/user-schema-0 (fern/lit
                        vase.datomic/attributes
                        [:user/userId :one :long :identity "A Users unique identifier"]
                        [:user/userEmail :one :string :unique "The users email"]
                        [:user/userBio :one :string :index :fulltext "A short blurb about the user"])
 example/v1 (fern/lit
             vase/api
             {:expose-api-at "/example/v1/api"
              :on-request [@connection]
              :on-startup [@connection @example/user-schema @example/loan-schema]
              :path "/example/v1"
              :routes @example.v1/routes})
```

I find that hard to read, thanks to the irregular indentation. I
prefer to put `fern/lit` and the literal type on the same line, and
line up all the right-hand sides. Like this:

```clojure
 example/user-schema-0        (fern/lit  vase.datomic/attributes
                                         [:user/userId    :one :long   :identity        "A Users unique identifier"]
                                         [:user/userEmail :one :string :unique          "The users email"]
                                         [:user/userBio   :one :string :index :fulltext "A short blurb about the user"])
 example/v1                   (fern/lit  vase/api
                                         {:expose-api-at "/example/v1/api"
                                          :on-request    [@connection]
                                          :on-startup    [@connection @example/user-schema @example/loan-schema]
                                          :path          "/example/v1"
                                          :routes        @example.v1/routes})
```

(For Emacs users, "C-c C" in Clojure mode will do all the alignment
for you. But you still have to remove line breaks before it works
nicely.)
