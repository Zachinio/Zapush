package com.example.zapush

import android.os.Build
import com.example.zapush.models.Variable
import com.example.zapush.utils.ReflectionUtils
import com.example.zapush.utils.Utils
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.expr.StringLiteralExpr
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
        methodName: String,
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
            executeVariableDeclare(fieldDeclaration.variables)
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
                is VariableDeclarationExpr -> executeVariableDeclare(expression.variables)
                is MethodCallExpr -> executeMethodCall(expression)
            }
        }
    }

    private fun executeVariableDeclare(variables: NodeList<VariableDeclarator>) {
        variables.forEach { variable ->
            val varName = variable.nameAsString
            var varValue: Any? = null

            when (val varValueExpr = variable.initializer.get()) {
                is StringLiteralExpr -> varValue = varValueExpr.value
            }
            val classObj = varValue?.let { it::class.java } ?: run { null }
            args[varName] = Variable(varName, varValue, classObj)
        }
    }

    private fun executeMethodCall(expr: MethodCallExpr): Any? {
        var methodObjInstance: Any? = null

        if (expr.hasScope()) {
            val scope = expr.scope.get()

            if (scope is MethodCallExpr) {
                methodObjInstance = executeMethodCall(scope.asMethodCallExpr())
            } else if (scope is NameExpr) {
                methodObjInstance = executeClassCall(scope.asNameExpr())
            }
        }
        val methodClass = if (methodObjInstance is Class<*>) {
            methodObjInstance
        } else {
            methodObjInstance!!::class.java
        }

        val method = ReflectionUtils.getMethod(
            methodClass,
            expr.nameAsString,
            expr.arguments,
            args,
            parse!!,
        )

        /* static method invokation */
        return if (methodObjInstance is Class<*>) {
            method.invoke(
                null,
                *ReflectionUtils.getMethodArgs(expr.arguments!!, args, parse!!.imports)
            )
        } else {
            method.invoke(
                methodObjInstance,
                *ReflectionUtils.getMethodArgs(expr.arguments!!, args, parse!!.imports)
            )
        }
    }

    private fun executeClassCall(nameExpr: NameExpr): Class<*> {
        val import = parse!!.imports.find { import ->
            import.name.identifier == nameExpr.nameAsString
        }
        //TODO add search for classes in Zapush or inner classes

        import
            ?: throw Utils.exceptionMessage("Couldn't find class with name ${nameExpr.nameAsString}")
        return Class.forName(import.nameAsString)
    }
}