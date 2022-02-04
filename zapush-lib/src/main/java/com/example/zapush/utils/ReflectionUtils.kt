package com.example.zapush.utils

import com.example.zapush.models.Variable
import com.github.javaparser.ast.ImportDeclaration
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.expr.*
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.math.exp

object ReflectionUtils {

    fun executeMethodCall(
        expr: MethodCallExpr, vars: HashMap<String, Variable>,
        imports: NodeList<ImportDeclaration>
    ): Any? {
        var methodObjInstance: Any? = null

        if (expr.hasScope()) {
            val scope = expr.scope.get()

            if (scope is MethodCallExpr) {
                methodObjInstance = executeMethodCall(scope.asMethodCallExpr(), vars, imports)
            } else if (scope is NameExpr) {
                methodObjInstance = if (vars.containsKey(scope.nameAsString)) {
                    vars[scope.nameAsString]?.instance!!
                } else {
                    executeClassCall(scope.nameAsString, imports, vars)
                }
            }
        }
        val methodClass = if (methodObjInstance is Class<*>) {
            methodObjInstance
        } else {
            methodObjInstance!!::class.java
        }

        val method = getMethod(
            methodClass,
            expr.nameAsString,
            expr.arguments,
            vars,
            imports,
        )

        return if (methodObjInstance is Class<*>) {
            /* static method invokation */
            method.invoke(
                null,
                *getMethodArgs(expr.arguments!!, vars, imports)
            )
        } else {
            /* instance method invokation */
            method.invoke(
                methodObjInstance,
                *getMethodArgs(expr.arguments!!, vars, imports)
            )
        }
    }

    internal fun getCondition(
        condition: Expression,
        imports: NodeList<ImportDeclaration>,
        vars: HashMap<String, Variable>
    ): Boolean {
        return when (condition) {
            is NameExpr -> vars[condition.nameAsString]!!.instance
            is BooleanLiteralExpr -> condition.value
            is IntegerLiteralExpr -> condition.value
            is MethodCallExpr -> executeMethodCall(condition, vars, imports)
            is EnclosedExpr -> getCondition(condition.inner, imports, vars)
            is BinaryExpr -> executeBinaryExpression(condition, imports, vars)
            else -> throw Utils.exceptionMessage("Failed to get condition value")
        } as Boolean
    }

    internal fun executeVariableDeclare(
        variables: NodeList<VariableDeclarator>, vars: HashMap<String, Variable>,
        imports: NodeList<ImportDeclaration>
    ) {
        variables.forEach { variable ->
            val varName = variable.nameAsString
            var varValue: Any? = null

            when (val varValueExpr = variable.initializer.get()) {
                is StringLiteralExpr -> varValue = varValueExpr.value
                is BooleanLiteralExpr -> varValue = varValueExpr.value
                is IntegerLiteralExpr -> varValue = varValueExpr.value.toInt()
                is ObjectCreationExpr -> {
                    varValue = createInstance(
                        executeClassCall(varValueExpr.typeAsString, imports, vars),
                        varValueExpr.arguments, vars, imports
                    )
                }
            }
            val classObj = varValue?.let { it::class.java } ?: run { null }
            vars[varName] = Variable(varName, varValue, classObj)
        }
    }

    internal fun getValueByExpression(vars: HashMap<String, Variable>, expr: Expression): Any? {
        return when (expr) {
            is IntegerLiteralExpr -> expr.value.toInt()
            is BooleanLiteralExpr -> expr.value
            is StringLiteralExpr -> expr.value
            is NameExpr -> vars[expr.nameAsString]!!.instance
            else -> throw Utils.exceptionMessage("Failed to get value of expression")
        }
    }

    private fun executeBinaryExpression(
        binaryExpr: BinaryExpr,
        imports: NodeList<ImportDeclaration>,
        vars: HashMap<String, Variable>
    ): Any {
        return when (binaryExpr.operator.name) {
            "OR" -> getCondition(binaryExpr.left, imports, vars) || getCondition(
                binaryExpr.right,
                imports,
                vars
            )
            "AND" -> getCondition(binaryExpr.left, imports, vars) &&
                    getCondition(binaryExpr.right, imports, vars)
            "LESS" -> (getValueByExpression(vars, binaryExpr.left) as Int) <
                    getValueByExpression(vars, binaryExpr.right) as Int
            "LESS_EQUALS" -> (getValueByExpression(vars, binaryExpr.left) as Int) <=
                    getValueByExpression(vars, binaryExpr.right) as Int
            "GREATER" -> (getValueByExpression(vars, binaryExpr.left) as Int) >
                    getValueByExpression(vars, binaryExpr.right) as Int
            "GREATER_EQUALS" -> (getValueByExpression(vars, binaryExpr.left) as Int) >=
                    getValueByExpression(vars, binaryExpr.right) as Int
            "PLUS" -> {
                val leftValue = getValueByExpression(vars, binaryExpr.left)
                val rightValue = getValueByExpression(vars, binaryExpr.right)
                return if (leftValue is Int) {
                    leftValue + rightValue.toString().toInt()
                } else {
                    leftValue.toString() + rightValue
                }
            }
            else -> throw Utils.exceptionMessage("Unknown binary operation ${binaryExpr.operator.name}")
        }
    }

    private fun getMethod(
        classObj: Class<*>,
        name: String,
        arguments: NodeList<Expression>,
        vars: HashMap<String, Variable>,
        imports: NodeList<ImportDeclaration>
    ): Method {
        val argumentsClasses = arguments.map { expression ->
            getClassByArg(expression, vars, imports)
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
        arguments: List<Class<*>?>
    ): List<Method> {
        return foundMethods.filter { method ->
            if (method.parameterTypes.size != arguments.size) {
                return@filter false
            }
            method.parameterTypes.forEachIndexed { index, parameter ->
                arguments[index]?.let {
                    if (!parameter.equals(arguments[index])
                        && !parameter.isAssignableFrom(it)
                    ) {
                        return@filter false
                    }
                }
            }
            return@filter true
        }
    }

    private fun getClassByArg(
        expression: Expression,
        vars: HashMap<String, Variable>,
        imports: NodeList<ImportDeclaration>
    ): Class<*>? {
        return when (expression) {
            is NameExpr -> vars[expression.asNameExpr().nameAsString]!!.className
            is StringLiteralExpr -> String::class.java
            is MethodCallExpr -> {
                // TODO HANDLE no scope
                getMethod(
                    getClassByArg(expression.scope.get(), vars, imports)!!,
                    expression.nameAsString,
                    expression.arguments,
                    vars,
                    imports
                ).returnType
            }
            is FieldAccessExpr -> getField(expression, vars, imports).type
            is BinaryExpr -> executeBinaryExpression(expression, imports, vars)::class.java
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
            is MethodCallExpr -> executeMethodCall(expression, vars, imports)
            is FieldAccessExpr -> getFieldValue(expression, vars, imports)
            is BinaryExpr -> executeBinaryExpression(expression, imports, vars)
            else -> throw Utils.exceptionMessage("Method by arg failed - can't find class of arg")
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
        if (fieldAccessExpr.scope is NameExpr) {
            return field.get(null)
        }
        return field.get(null)
    }

    private fun getMethodArgs(
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

    private fun createInstance(
        classObj: Class<*>,
        arguments: NodeList<Expression>,
        vars: HashMap<String, Variable>,
        imports: NodeList<ImportDeclaration>
    ): Any? {
        val argumentsClasses = arguments.map { expression ->
            getClassByArg(expression, vars, imports)
        }
        val constructors = classObj.constructors.filter { constructor ->
            if (constructor.parameterTypes.size != arguments.size) {
                return@filter false
            }
            constructor.parameterTypes.forEachIndexed { index, type ->
                argumentsClasses[index]?.let {
                    if (!type.equals(arguments[index]::class.java)
                        && !type.isAssignableFrom(it)
                    ) {
                        return@filter false
                    }
                }
            }
            return@filter true
        }
        if (constructors.isEmpty()) {
            throw Utils.exceptionMessage("Failed to find constructor for ${classObj.name}")
        }
        return constructors[0].newInstance(*getMethodArgs(arguments, vars, imports))
    }

    private fun getBuiltClass(className: String): Class<*>? {
        return when (className) {
            "String" -> Class.forName("java.lang.String")
            else -> null
        }
    }


    private fun executeClassCall(
        className: String, imports: NodeList<ImportDeclaration>, vars: HashMap<String, Variable>
    ): Class<*> {
        val import = imports.find { import ->
            import.name.identifier == className
        }
        //TODO add search for classes in Zapush or inner classes
        if (import != null) {
            return Class.forName(import.nameAsString)
        }
        getBuiltClass(className)?.let {
            return it
        }
        throw Utils.exceptionMessage("Couldn't find class with name $className")
    }
}

