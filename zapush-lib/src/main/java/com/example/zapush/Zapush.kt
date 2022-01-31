package com.example.zapush

import com.example.zapush.models.Variable
import com.example.zapush.utils.ReflectionUtils
import com.example.zapush.utils.Utils
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.VariableDeclarationExpr
import com.github.javaparser.ast.stmt.ExpressionStmt
import com.github.javaparser.utils.SourceRoot
import java.io.File
import kotlin.io.path.Path

class Zapush {

    private var parse: CompilationUnit? = null
    private var foundClass: ClassOrInterfaceDeclaration? = null
    private var foundMethod: MethodDeclaration? = null
    private var args = hashMapOf<String, Variable>()

    fun execute(
        javaFile: File,
        className: String,
        methodName: String = "main",
        vars: HashMap<String, Any>
    ) {
        if (!javaFile.exists() || javaFile.parent == null) {
            throw Utils.exceptionMessage("Java file not found")
        }
        val sourceRoot = SourceRoot(Path(javaFile.parent!!))
        parse = sourceRoot.parse("", javaFile.name)
        checkParsed(parse)

        foundClass = parse?.childNodes
            ?.filterIsInstance<ClassOrInterfaceDeclaration>()
            ?.find { it.nameAsString == className }

        if (foundClass == null || foundClass!!.isEmpty) {
            throw Utils.exceptionMessage("Couldn't find the class $className")
        }

        foundMethod = foundClass!!.members
            .filterIsInstance<MethodDeclaration>()
            .find { it.nameAsString == methodName }

        foundMethod ?: throw Utils.exceptionMessage("Failed to find method $methodName")

        addVars(vars)
        executeLines(foundMethod!!)
    }

    private fun addVars(vars: java.util.HashMap<String, Any>) {
        vars.iterator().forEach {
            args[it.key] = Variable(it.key, it.value, getVariableClass(it.key, it.value))
        }
        foundClass?.members?.filterIsInstance<FieldDeclaration>()?.forEach { fieldDeclaration ->
            ReflectionUtils.executeVariableDeclare(
                fieldDeclaration.variables,
                args,
                parse!!.imports
            )
        }
    }

    private fun getVariableClass(key: String, value: Any): Class<*> {
        foundMethod!!.parameters.forEach { parameter ->
            if (parameter.nameAsString == key) {
                parse!!.imports.find { it.name.identifier == parameter.typeAsString }
                    ?.let {
                        return Class.forName(it.nameAsString)
                    }
            }
        }
        return value.javaClass
    }

    private fun checkParsed(parse: CompilationUnit?) {
        if (parse == null || parse.parsed.name != "PARSED") {
            throw Utils.exceptionMessage("Java file can't be parsed")
        }
    }

    private fun executeLines(foundMethod: MethodDeclaration) {
        val methodCode = foundMethod.body.get()
        val codeLines = methodCode.statements.filterIsInstance<ExpressionStmt>()

        codeLines.forEach { codeLine ->
            when (val expression = codeLine.expression) {
                is VariableDeclarationExpr -> ReflectionUtils.executeVariableDeclare(
                    expression.variables,
                    args,
                    parse!!.imports
                )
                is MethodCallExpr -> ReflectionUtils.executeMethodCall(
                    expression,
                    args,
                    parse!!.imports
                )
            }
        }
    }
}