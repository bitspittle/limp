package dev.bitspittle.limp

import com.varabyte.truthish.assertThat
import com.varabyte.truthish.assertThrows
import dev.bitspittle.limp.exceptions.EvaluationException
import dev.bitspittle.limp.methods.collection.FilterMethod
import dev.bitspittle.limp.methods.collection.ListMethod
import dev.bitspittle.limp.methods.compare.GreaterThanEqualsMethod
import dev.bitspittle.limp.methods.math.*
import dev.bitspittle.limp.methods.range.IntRangeMethod
import dev.bitspittle.limp.methods.system.DefMethod
import dev.bitspittle.limp.methods.system.SetMethod
import dev.bitspittle.limp.types.ConsoleLogger
import dev.bitspittle.limp.types.Placeholder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@Suppress("UNCHECKED_CAST")
class EvaluatorTest {
    @Test
    fun testSimpleEvaluation() = runTest {
        val env = Environment()
        env.addMethod(AddMethod())
        env.addMethod(MulMethod())
        env.addMethod(SubMethod())

        val evaluator = Evaluator()
        assertThat(evaluator.evaluate(env, "+ 1 2")).isEqualTo(3)
        assertThat(evaluator.evaluate(env, "+ 1 * 3 2")).isEqualTo(7)
        assertThat(evaluator.evaluate(env, "+ 1 * 3 - 8 2")).isEqualTo(19)
        assertThat(evaluator.evaluate(env, "(+ 1 (* 3 (- 8 2)))")).isEqualTo(19)

        env.storeValue("\$a", 5)
        env.storeValue("\$b", 90)
        assertThat(evaluator.evaluate(env, "+ \$a (* 2 \$b)")).isEqualTo(185)
    }

    @Test
    fun testEvaluationWithPlaceholder() = runTest {
        val env = Environment()
        env.addMethod(IntRangeMethod())
        env.storeValue("_", Placeholder)

        val evaluator = Evaluator()
        assertThat(evaluator.evaluate(env, ".. 1 _")).isEqualTo(1 .. Int.MAX_VALUE)
    }

    @Test
    fun testEvaluationWithOptionalParameters() = runTest {
        val env = Environment()
        env.addMethod(IntRangeMethod())

        val evaluator = Evaluator()
        assertThat(evaluator.evaluate(env, ".. 1 10")).isEqualTo(1 .. 10)
        assertThat(evaluator.evaluate(env, ".. --step 4 1 20")).isEqualTo(1 .. 20 step 4)

        assertThrows<EvaluationException> {
            // Whoops, typo!
            assertThat(evaluator.evaluate(env, ".. --stop 4 1 20")).isEqualTo(1..20 step 4)
        }
    }

    @Test
    fun testEvaluationWithRestParameters() = runTest {
        val env = Environment()
        env.addMethod(ListMethod())
        env.addMethod(AddMethod())
        env.addMethod(AddListMethod())
        env.addMethod(MulListMethod())

        val evaluator = Evaluator()
        assertThat(evaluator.evaluate(env, "list 1 2 3 4 5") as List<Int>).containsExactly(1, 2, 3, 4, 5)
            .inOrder()

        assertThat(evaluator.evaluate(env, "sum list 1 2 3 4 5")).isEqualTo(15)
        assertThat(evaluator.evaluate(env, "+ (mul list 1 2 3) (sum list 4 5 6)")).isEqualTo(21)
    }

    @Test
    fun methodExceptionsWillGetRethrownAsEvaluationExceptions() = runTest {
        val env = Environment()
        env.addMethod(DivMethod())

        val evaluator = Evaluator()
        assertThrows<EvaluationException> {
            evaluator.evaluate(env, "/ 10 0")
        }
    }

    // Originally, we used to bind method parameters to variables in the environment, but those would leak into other
    // function calls, which wasn't expected (because variables in the environment are kinda global, while method
    // parameters should be hyper local
    @Test
    fun testEvaluationClosureLogic() = runTest {
        val env = Environment()
        env.addMethod(DefMethod())
        env.addMethod(AddMethod())
        env.addMethod(SetMethod(ConsoleLogger()))
        env.addMethod(ListMethod())
        env.addMethod(FilterMethod())
        env.addMethod(GreaterThanEqualsMethod())

        val evaluator = Evaluator()
        evaluator.evaluate(env, "set '\$ints (list 1 2 3 4 5)")

        // Basic closure case works
        env.scoped {
            evaluator.evaluate(env, "def 'filter-gte '\$list '\$low '(filter \$list '(>= \$it \$low))")
            assertThat(evaluator.evaluate(env, "filter-gte \$ints 3") as List<Int>)
                .containsExactly(3, 4, 5)
                .inOrder()
        }

        // Parameters don't leak out of their own local scope
        env.scoped {

            evaluator.evaluate(env, "def 'add-ab '(+ \$a \$b)")
            evaluator.evaluate(env, "def 'add '\$a '\$b 'add-ab")

            assertThrows<EvaluationException> {
                evaluator.evaluate(env, "add 1 2")
            }
        }

        // Method parameters take precedence over variables created elsewhere
        env.scoped {
            evaluator.evaluate(env, "def 'sum 'a 'b '(+ a b)")
            evaluator.evaluate(env, "set 'a 99")
            evaluator.evaluate(env, "set 'b 100")
            assertThat(evaluator.evaluate(env, "sum 1 2")).isEqualTo(3)
        }
    }
}