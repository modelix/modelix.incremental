package org.modelix.incremental

import kotlin.test.BeforeTest
import kotlin.test.Test

class HtmlGeneratorTest {
/*
    var engine = IncrementalEngine()

    @BeforeTest
    fun beforeTest() {
        engine = IncrementalEngine()
    }

    @Test
    fun test() {
        val copyProperty: (MNode, String, MNode, String)->Unit = engine.incrementalFunction {
                context, sourceNode, sourceRole, targetNode, targetRole ->
            targetNode.property(targetRole, sourceNode.getProperty(sourceRole))
        }
        val propertyToHtml: (MNode, MNode)->MNode = engine.incrementalFunction { context, htmlParent, entity ->
            val propertyHtml = context.getPreviousResultOrElse { htmlParent.child("DIV", "") {} }
            propertyHtml.apply {
                child("DIV", "children") {
                    child("Text", "children") {
                        copyProperty(entity, "name", propertyHtml, "text")
                    }
                }
            }
            propertyHtml
        }
        val entityToHtml: (MNode, MNode)->MNode = engine.incrementalFunction { context, htmlParent, entity ->
            val entityHtml = context.getPreviousResult() ?: MNode("DIV")
            entityHtml.apply {

            }
            entityHtml
        }
        val rootNode = MNode("Root").apply {
            child("Entity", "entities") {
                property("name", "EntityA")
                child("Property", "properties") {
                    property("name", "propertyA1")
                }
                child("Property", "properties") {
                    property("name", "propertyA2")
                }
            }
        }
    }

    fun rootToHtml(root: MNode): MNode {
        MNode("HTML").apply {
            child("BODY", "") {
                child("DIV", "") {
                    for (entity in root.getChildren("entities")) {
                        child("TABLE", "")
                    }
                }
            }
        }
    }

 */
}