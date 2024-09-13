package com.phodal.shirelang.regression

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.phodal.shirecore.config.ShireActionLocation
import com.phodal.shirecore.config.InteractionType
import com.phodal.shirecore.middleware.PostProcessorContext
import com.phodal.shirelang.compiler.parser.HobbitHoleParser
import com.phodal.shirelang.compiler.ShireSyntaxAnalyzer
import com.phodal.shirelang.compiler.ShireTemplateCompiler
import com.phodal.shirelang.compiler.hobbit.ast.LogicalExpression
import com.phodal.shirelang.compiler.patternaction.PatternActionFunc
import com.phodal.shirelang.compiler.hobbit.execute.PatternActionProcessor
import com.phodal.shirelang.psi.ShireFile
import junit.framework.TestCase
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language

class ShireCompileTest : BasePlatformTestCase() {
    val javaHelloController = """
            package com.phodal.shirelang.controller;
            
            import org.springframework.web.bind.annotation.GetMapping;
            import org.springframework.web.bind.annotation.RestController;
            
            @RestController
            public class HelloController {
                @GetMapping("/hello")
                public String hello() {
                    return "Hello, World!";
                }
            }
        """.trimIndent()

    val javaHelloEntity = """
            package com.phodal.shirelang.entity;
            
            public class HelloEntity {
                private String name;
            
                public String getName() {
                    return name;
                }
            
                public void setName(String name) {
                    this.name = name;
                }
            }
        """.trimIndent()

    fun testShouldReturnControllerCodeWithFindCat() {
        myFixture.addFileToProject(
            "src/main/java/com/phodal/shirelang/controller/HelloController.java",
            javaHelloController
        )
        myFixture.addFileToProject("src/main/java/com/phodal/shirelang/entity/HelloEntity.java", javaHelloEntity)

        @Language("Shire")
        val code = """
            ---
            name: "类图分析"
            variables:
              "controllers": /.*.java/ { find("Controller") | grep("src/main/java/.*")  | cat }
              "outputFile": /any/ { print("name.adl") }
            onStreamingEnd: { parseCode | saveFile(${'$'}outputFile) }
            ---
            
            
            请将下列信息原样输出，不要添加任何其他描述信息：
            
            ${'$'}controllers
        """.trimIndent()

        val file = myFixture.configureByText("test.shire", code)
        val compile = ShireSyntaxAnalyzer(project, file as ShireFile, myFixture.editor).parse()
        val hole = compile.config!!

        val context = PostProcessorContext(
            genText = "User prompt:\n\n",
        )

        runBlocking {
            val templateCompiler = ShireTemplateCompiler(project, hole, compile.variableTable, code)
            val compiledVariables =
                templateCompiler.compileVariable(myFixture.editor)

            context.compiledVariables = compiledVariables
        }

        assertEquals(
            """package com.phodal.shirelang.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {
    @GetMapping("/hello")
    public String hello() {
        return "Hello, World!";
    }
}""", context.compiledVariables["controllers"]
        )
    }

    fun testShouldReturnControllerCodeWithFindCatWithHead() {
        myFixture.addFileToProject(
            "src/main/java/com/phodal/shirelang/controller/HelloController.java",
            javaHelloController
        )
        myFixture.addFileToProject("src/main/java/com/phodal/shirelang/entity/HelloEntity.java", javaHelloEntity)

        @Language("Shire")
        val code = """
            ---
            name: "类图分析"
            variables:
              "output": "name.adl"
              "con": /.*.java/ { print | head(1)}
              "controllers": /.*.java/ { find("Controller") | grep("src/main/java/.*") | head(1)  | cat   }
              "outputFile": /any/ { print("name.adl") }
            onStreamingEnd: { parseCode | saveFile(${'$'}outputFile) }
            
            ---
            
            下面是你要执行转换的数据：
            ${'$'}controllers
        """.trimIndent()

        val file = myFixture.configureByText("test.shire", code)
        val compile = ShireSyntaxAnalyzer(project, file as ShireFile, myFixture.editor).parse()
        val hole = compile.config!!

        val context = PostProcessorContext(
            genText = "User prompt:\n\n",
        )

        runBlocking {
            val templateCompiler = ShireTemplateCompiler(project, hole, compile.variableTable, code)
            val compiledVariables =
                templateCompiler.compileVariable(myFixture.editor)

            context.compiledVariables = compiledVariables
        }

        assertEquals(
            """[/src/src/main/java/com/phodal/shirelang/entity/HelloEntity.java]""",
            context.compiledVariables["con"]
        )
        assertEquals("package com.phodal.shirelang.controller;\n" +
                "\n" +
                "import org.springframework.web.bind.annotation.GetMapping;\n" +
                "import org.springframework.web.bind.annotation.RestController;\n" +
                "\n" +
                "@RestController\n" +
                "public class HelloController {\n" +
                "    @GetMapping(\"/hello\")\n" +
                "    public String hello() {\n" +
                "        return \"Hello, World!\";\n" +
                "    }\n" +
                "}", context.compiledVariables["controllers"])
    }
}
