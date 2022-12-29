# limp 

This project implements a minimal, dynamic, Lisp-inspired toy language that works in a Kotlin multiplatform context.

## Trying it out

This project includes a simple interpreter which allows you to experiment with the language live.

To run it:

```bash
$ ./gradlew :limp-interpreter:installDist
$ ./limp-interpreter/build/install/limp-interpreter/bin/limp-interpreter
```

Example run:
```bash
> + 5 10
15
> * 5 - 25 15
50
> + 10 - 20 / 30 0

Method "/" threw an exception while trying to run:
  Divide by 0

  Error occured here:

  + 10 - 20 / 30 0
            ^

> take --from 'back (.. 1 10) 3
[8, 9, 10]

> set '$test 123
> $test
123
> quit
```

## Language

### Expressions

Limp is designed to be very simple, with a limited number of expression types:

- Quoted strings (e.g. `"Hello"`)
- Integers (e.g. `123` or `-456`)
- Symbols (e.g. `some-identifier-name` or `+`)
- Options (e.g. `--param 123`)
- Blocks (e.g. `(+ 1 2)`)
- Deferred expressions (e.g. `'symbol` or `'(+ 1 2)`)

You can also use the `#` symbol to indicate a comment that runs to the end of the line.

### Polish Notation

For parsing simplicity, Limp uses polish (also called *prefix*) notation. That is, addition looks like `(+ 1 2)` instead
of `(1 + 2)`.

This removes ambiguity if parentheses aren't used. That is, `(* 2 + 5 1)` is always 12, while in a language like Java,
evaluating `2 * 5 + 1` depends on knowledge of operator precedence, where there it evaluates to 11 unless you
explicitly add parentheses (as in `2 * (5 + 1)`).

### Deferred expressions

By default, when you evaluate a Limp expression, every part of the expression is evaluated immediately. However, by
prefixing parts of the expression with an apostrophe (`'`), it tells the evaluator to postpone evaluation on that for
later.

A concrete example is the "set variable" method, which looks like this: `set '$example 123`. If you didn't defer the
variable name there, instead writing `set $example 123`, the evaluator would try to evaluate the variable immediately
and barf because it hasn't been defined yet!

Another example for deferment is for lambdas. Here's filter: `filter $numbers '(>= $it 0)`.
This expression means "take in a list of numbers and return a new list which only has positive numbers in it". We don't
want to run the logic for checking if a number is positive immediately! Instead, we want to let the `filter` method call
it internally. (It is also up to the `filter` method to correctly define the `$it` variable for us.)

### Differences from Lisp

_This is not a complete list but a glance at some larger deviations._

- Designed for Kotlin Multiplatform
- Optional parameters for methods
- Method naming convention taken from Ruby ("dangerous" methods end with a `!`, query methods end with a `?`)
- Comments use the `#` symbol
- Only supported primitives types are _String_ and _Int_
- No currying support. Methods either take in a fixed number of parameters or they consume the rest of the block

## Embedding Limp in your Kotlin project

Limp works by combining two classes, `Environment` and `Evaluator`.

### Environment

An environment is a scoped collection of methods, variables, and converters.

It is trivial to construct one: `val env = Environment()`.

The environment can be used to look up variables and methods by name as well as attempt to convert values (since as a
dynamic language, all input values come in as `Any?` initially).

When constructed, a new environment is totally empty, but you can use the provided utility method,
`Environment.installDefaults()`, to add a bunch of useful logic, math, and other generally helpful behavior. (You
probably want to do this!)

Once you've installed the defaults, you are free to add additional methods (and values) to the environment that are
explicitly designed for your application.

### Method

You can define methods by implementing a `Method` base class.

A method is some logic, tagged with a name and specifying the number of arguments it can consume.

For example, the add method takes two arguments (and returns their sum, if the two arguments are integers).

```kotlin
// "Add" is a method represented by the method name "+" and it takes exactly 2 arguments
class AddMethod : Method("+", 2) {
    override suspend fun invoke(env: Environment, params: List<Any>, /*...*/): Any {
        val a = env.expectConvert<Int>(params[0])
        val b = env.expectConvert<Int>(params[1])
        return a + b
    }
}
```

#### consumeRest

A method can additionally be configured to accept optional parameters as well as to consume all remaining arguments in
the expression. The latter is useful for a method that can take a dynamic number of arguments, say a method like
`(list 1 2 3 4 5 6)`.

```kotlin
// By passing in `consumeRest = true`, the `rest` parameter to the `invoke` method will be filled with values (if any).
class ListMethod : Method("list", 0, consumeRest = true) {
    override suspend fun invoke(/*...*/ rest: List<Any>): Any {
        // Return a copy of the list; the originally passed in list will be collected
        return rest.toList()
    }
}
```

#### Optional parameters

One way that Limp differs from Lisp is support for optional parameters in methods. These are named parameters prefixed
by `--`.

For example, the `take` method receives a list and takes *n* elements from it, essentially making a copy of some subset
of the list. It *also* allows you to specify where those items are taken from, using the `--from` parameter. If not
explicitly specified, the method takes the items from the front.



```kotlin
enum class ListStrategy {
    FRONT,
    BACK,
}

// Note, for example simplicity, this is a pared down version than what's actually defined
// Here, we can take from front or back of the list, using `--from 'front` and `--from 'back`
class TakeMethod(private val random: () -> Random) : Method("take", 2) {
    override suspend fun invoke(/*...*/ options: Map<String, Any>, /*...*/): Any {
        val list = env.expectConvert<List<Any>>(params[0])
        val count = env.expectConvert<Int>(params[1]).coerceAtMost(list.size)

        val strategy =
            options["from"]?.let { from ->
                env.expectConvert<Expr.Identifier>(from).toEnum(ListStrategy.values())
            } ?: ListStrategy.FRONT

        return when (strategy) {
            ListStrategy.FRONT -> list.take(count)
            ListStrategy.BACK -> list.takeLast(count)
        }
    }
}
```

### Variable

A variable is a value tagged with a name.

You can register these directly into the environment using `env.storeValue("\$example", 123)`. Or, if you added the
`SetMethod` into the environment, a user can define a variable using syntax like `set '$example 123`

By convention, when you define a variable, you should prepend it with a `$`, for readability. However, it's not strictly
required you do so.

### Converter

A converter is some logic to automatically convert a value of one type into another, at runtime. This can be useful to
make some of your methods a bit more flexible.

For example, if you have a method that takes in a String, but it gets passed a single character, you can create a
converter that converts non-list items into a singleton list for you on the fly.

```kotlin
class CharToStringConverter : Converter<String>(String::class) {
    override fun convert(value: Any): String? {
        return (value as? Char)?.toString() // Returning a non-null response indicates a successful conversion
    }
}
```

Then, in your method, if you have such a converter added to the environment, then this line:

```kotlin
val str = env.expectConvert<String>(params[0])
```

will pass `params[0]` through if it's already a `String`, will convert it into a `String` if it's a `Char`, or an
exception will get thrown if it's another type (and no other relevant converters are found)

You should be careful with abusing converters, however, as if there are too many, an unexpected conversion might happen
behind your back.

### Scope

Earlier, we wrote that environments are *scoped*. 

What this means is you can use `pushScope` (and `popScope`) at any point to introduce (and later remove) a new
local scope. All methods, variables, and converters registered while this scope is active will be discarded if it is
ever removed.

There is also a convenience `scoped` method that handles calling `pushScope` and `popScope` for you (even if an
exception gets thrown in the middle).

Scoping is a very useful feature when you are implementing the logic of a method that wants to support type conversions
that shouldn't be added globally. Recalling the `CharToStringConverter` example from the previous section, here's how
the `LowerMethod` is implemented:

```kotlin
class LowerMethod : Method("lower", 1) {
    override suspend fun invoke(env: Environment, params: List<Any>, /* ... */): Any {
        return env.scoped {
            env.addConverter(CharToStringConverter())
            val str = env.expectConvert<String>(params[0])
            str.lowercase()
        }
    }
}
```

### Placeholder

Limp defines a special character, called the `Placeholder`, represented by `_`.

On its own, it is an inert thing, but you can define methods which check if a Placeholder was passed in as an argument.
If so, you could use it as an indicator that the user wants to use some default value.

Limp also provides a `PlacholderConverter` class you can use to accomlish this behavior in your own method.

For example, the `IntRange` method uses this to allow people to shortcut passing in an upper bound value:

```kotlin
class IntRangeMethod : Method("..", 2) {
    override suspend fun invoke(env: Environment, params: List<Any>, /*...*/): Any {
        val start = env.expectConvert<Int>(params[0])
        val end = env.scoped {
            env.addConverter(PlaceholderConverter(Int.MAX_VALUE))
            env.expectConvert<Int>(params[1])
        }

        return IntRange(start, end)
    }
}
```

With this placeholder in place, users can express they want a range but don't care about its maximum value. Using
`.. 5 _` becomes a shorthand syntax allowing you to avoid writing something hacky like `.. 5 99999` (or
`.. 5 $MAX_INT_VALUE` if you end up defining such a value yourself in your own Limp environment).

### Evaluator

An evaluator is a class which is responsible for taking a code statement and an accompanying environment and processing
the two together to produce a result.

Most of the time you can just instance an evaluator anywhere you have an environment and fire its `evaluate` method:

```kotlin
val env: Environment = createAndInitializeEnvironment()
val evaluator = Evaluator()
evaluator.evaluate(env, "+ 1 2") // returns 3 (as `Any`)
```

This will both parse the code AND run through the processed result, pulling values out of and writing others back into
the environment as it goes along. If it fails at any point, it will throw an `EvaluationException` explaining what went
wrong.

#### Parsing Expressions

Although evaluators handle parsing for you, it's trivial to parse a limp expression on your own. Just use the
`Expr.parse` method:

```kotlin
val compiled = Expr.parse("+ 1 2")
```

You can feed in such compiled results into an evaluator, e.g. `evaluator.evaluate(env, compiled)`, instead of raw code.
Passing in a pre-compiled result can potentially save work if you end up evaluating the same expression over and over
again.

This change is unlikely to matter too much in most applications -- if performance is *that* critical for you, you
probably should look elsewhere for better gains -- but it can be useful to compile all your code upfront, so that an
accidental syntax error will be caught at startup time instead of hours later at runtime.

### Code Examples

Bringing everything together:

```kotlin
// The basics
val env = Environment()
env.installDefaults()

val evaluator = Evaluator()
val result = evaluator.evaluate(env, "* 2 + 5 1")
assertThat(result).isEqualTo(12)
```

```kotlin
// Defining a method
val env = Environment()
env.add(object : Method("concat", 2) {
   override suspend fun invoke(env: Environment, params: List<Value>, rest: List<Value>): Value {
       return env.expectConvert<String>(params[0]) + env.expectConvert<String>(params[1])
   }
})

val evaluator = Evaluator()
val result = evaluator.evaluate(env, "concat \"Hello \" \"World\"")
assertThat(result).isEqualTo("Hello World")
```

```kotlin
// Using a converter

val env = Environment()
// greeting "Joe" # Returns "Hello Joe"
// greeting _ # Returns "Hello World"
env.add(object : Method("greeting", 1) {
   override suspend fun invoke(env: Environment, params: List<Value>, optionals: Map<String, Value>, rest: List<Value>): Value {
       return env.scoped {
           env.add(PlaceholderConverter("World"))
           "Hello " + env.expectConvert<String>(params[0])
       }
   }
})
```
