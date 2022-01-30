package com.example.zapush.utils

import com.example.zapush.models.Variable
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.ImportDeclaration
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.FieldAccessExpr
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.expr.StringLiteralExpr
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.collections.HashMap

object ReflectionUtils {

    fun getMethod(
        classObj: Class<*>,
        name: String,
        arguments: NodeList<Expression>,
        vars: HashMap<String, Variable>,
        parse: CompilationUnit
    ): Method {
        val argumentsClasses = arguments.map { expression ->
            getClassByArg(expression, vars, parse.imports)
        }
        var foundMethods = classObj.methods.filter { method ->
            method.name == name
        }
        if (foundMethods.isEmpty()) {
            throw Utils.exceptionMessage("Couldn't find method $name in class ${classObj.name}")
        }
        if (foundMethods.size > 1) {
            foundMethods = findMatchedMethods(foundMethods, argumentsClasses)
        }
        if (foundMethods.size != 1) {
            throw Utils.exceptionMessage("Couldn't find method after filtering")
        }
        return foundMethods[0]
    }

    private fun findMatchedMethods(
        foundMethods: List<Method>,
        arguments: List<Class<*>>
    ): List<Method> {
        return foundMethods.filter { method ->
            if (method.parameterTypes.size != arguments.size) {
                return@filter false
            }
            method.parameterTypes.forEachIndexed { index, parameter ->
                if (!parameter.equals(arguments[index])
                    && !parameter.isAssignableFrom(arguments[index])
                ) {
                    return@filter false
                }
            }
            return@filter true
        }
    }

    private fun getClassByArg(
        expression: Expression,
        vars: HashMap<String, Variable>,
        imports: NodeList<ImportDeclaration>
    ): Class<*> {
        return when (expression) {
            is NameExpr -> vars[expression.asNameExpr().nameAsString]!!.className
            is StringLiteralExpr -> String::class.java
            is FieldAccessExpr -> getField(
                expression.asFieldAccessExpr(),
                vars,
                imports
            ).type
            else -> throw Utils.exceptionMessage("Class by arg failed - can't find class of arg")
        }
    }

    private fun getMethodArgValue(
        expression: Expression,
        vars: HashMap<String, Variable>,
        imports: NodeList<ImportDeclaration>
    ): Any? {
        return when (expression) {
            is NameExpr -> vars[expression.nameAsString]!!.instance
            is StringLiteralExpr -> expression.value
            is FieldAccessExpr -> getFieldValue(expression, vars, imports)
            else -> throw Utils.exceptionMessage("Class by arg failed - can't find class of arg")
        }
    }

    private fun getField(
        fieldAccessExpr: FieldAccessExpr,
        vars: HashMap<String, Variable>,
        imports: NodeList<ImportDeclaration>
    ): Field {
        val scope = fieldAccessExpr.scope
        if (scope is NameExpr) {
            imports.find { it.name.identifier == scope.asNameExpr().nameAsString }?.let {
                return Class.forName(it.nameAsString).getField(fieldAccessExpr.nameAsString)
            }
        }
        //TODO add handling for non static
        throw Utils.exceptionMessage("getfield failed")
    }

    private fun getFieldValue(
        fieldAccessExpr: FieldAccessExpr,
        vars: HashMap<String, Variable>,
        imports: NodeList<ImportDeclaration>
    ): Any? {
        val field = getField(fieldAccessExpr, vars, imports)
        if(fieldAccessExpr.scope is NameExpr){
            return field.get(null)
        }
        return field.get(null)
    }

    fun getMethodArgs(
        methodArgs: NodeList<Expression>,
        vars: java.util.HashMap<String, Variable>,
        imports: NodeList<ImportDeclaration>
    ): Array<Any> {
        val argsMatched = arrayListOf<Any?>()
        methodArgs.forEach { parameter ->
            argsMatched.add(getMethodArgValue(parameter, vars, imports))
        }
        return argsMatched.toArray()
    }

}