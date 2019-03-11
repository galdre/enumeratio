# enumeratio

*enumeratio*, Latin noun: An enumeration, list, or summation

This library is an alpha-quality implementation of enumeration types in Clojure(script). In the Clojure world, it is traditional to use keywords as a kind of enumerated constant. This is fine, and this library will work with that. In the past, this pattern sometimes meant that there was no single source of truth to say what were valid keywords and what were not. With a bit of programmer diligence, or with tools like `spec`, this is largely a solved problem. However, whenever these enumerated constants have implications for business logic, one tends to see a great deal of code like this:

```clj
(cond (= ::import/foo x)
      (do-something ...)
	  (= ::import/bar x)
	  (do-elsething ...))

;; Or perhaps:

(case x
   ::import/foo (do-something ...)
   ::import/bar (do-elsething ...))
```

This is less than ideal, especially if the programmer fails to specify a default condition. In particular, this is troublesome because this scatters knowledge of the enumerated set across the codebase. Again, a diligent programmer will avoid doing this, but Clojure does not provide any built-in tools for this.

*enumeratio* provides a tool for declarating a set of keywords known at compile-time, which correspond to a specific type (which can then implement protocols and interfaces). Example:

```clj
(defenum status
   #{::pending ::completed}
  my-library/SomeProtocol
  (foo [status-kw] ...)
  ...)
```

This does the follwoing:

1. it defines `status` as an `enumeratio.core.Enumeration`.
2. It defines a new type, `StatusElement`, which implements `SomeProtocol`.
3. It defines two vars, `status:pending` and `status:completed` that are instances of `StatusElement`.
4. It defines a function, `status-for`, which maps keywords to the appropriate `StatusElement` (or nil, if not declared in the enumeration).

Furthermore, each of the vars so generated has reasonable documentation attached.

```
(if-let [status (st/status-for ::st/pending)]
  (foo status)
  (throw
    (Exception.
	  "That's not a valid status!")
```

Knowledge encapsulated.

Warning: this library may be buggy. Its current state is the result of half a weekend of hacking, so proceed with caution!
