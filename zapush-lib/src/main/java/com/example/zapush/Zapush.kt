package com.example.zapush

import com.example.zapush.models.Variable
import com.example.zapush.utils.ReflectionUtils
import com.example.zapush.utils.Utils
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.ExpressionStmt
import com.github.javaparser.ast.stmt.IfStmt
import com.github.javaparser.ast.stmt.Statement
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
        executeLines(foundMethod!!.body.get())
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

    private fun executeLines(blockCode: BlockStmt) {
        val codeLines = blockCode.statements

        codeLines.forEach { codeLine ->
            when (codeLine) {
                is ExpressionStmt -> executeExpression(codeLine)
                is Statement -> executeStatement(codeLine)
            }
        }
    }

    private fun executeExpression(expressionStmt: ExpressionStmt) {
        when (val expression = expressionStmt.expression) {
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

    private fun executeStatement(statement: Statement) {
        when (statement) {
            is IfStmt -> executeIfStatement(statement)
        }
    }

    private fun executeIfStatement(ifStmt: IfStmt) {
        val condition = getCondition(ifStmt.condition)

        if (condition) {
            if (ifStmt.thenStmt != null) {
                executeLines(ifStmt.thenStmt.asBlockStmt())
            }
        } else if (ifStmt.hasElseBlock()) {
            executeLines(ifStmt.elseStmt.get().asBlockStmt())
        }

    }

    private fun getCondition(condition: Expression): Boolean {
        return when (condition) {
            is NameExpr -> args[condition.nameAsString]!!.instance
            is BooleanLiteralExpr -> condition.value
            is MethodCallExpr -> ReflectionUtils.executeMethodCall(
                condition,
                args,
                parse!!.imports
            )
            is EnclosedExpr -> getCondition(condition.inner)
            is BinaryExpr -> {
                if (condition.operator.name == "OR") {
                    return getCondition(condition.left) || getCondition(condition.right)
                }
                return getCondition(condition.left) && getCondition(condition.right)
            }
            else -> throw Utils.exceptionMessage("Failed to get condition value")
        } as Boolean
    }
}