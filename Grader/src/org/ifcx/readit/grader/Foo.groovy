// package org.ifcx.readit.grader
// Say we're in the same package as DecisionTree so IntelliJ will autocomplete package scope fields.
package cc.mallet.classify

@Grab(group = 'cc.mallet', module = 'mallet', version = '2.0.7')

import cc.mallet.classify.DecisionTree
import groovy.xml.StreamingMarkupBuilder

def model_file = new File(args[0])
def model = model_file.withObjectInputStream(this.class.classLoader) { (DecisionTree) it.readObject() }

new File(args[1]).text = new StreamingMarkupBuilder().bind() {
    html {
        body {
            h3 model_file.path

            def node2table = { DecisionTree.Node node ->
                if (node && !node.isLeaf()) {
                    table(border: 1) {
//                        tr { td(colspan:2, align:center, node.isRoot() ? "root" : "$node.splitFeature $node.splitInfoGain") }
//                        tr { td(colspan:2, align:'center', node.name + ' ' + node.splitInfoGain) }
                        tr {
                            td(colspan:2, align:'center') { div(node.name) ; div(node.splitInfoGain) }
                            tr { td { node2table(node.featureAbsentChild) }; td { node2table(node.featurePresentChild) } }
                        }
                    }
                } else {
                    // These aren't the labels we want. They look like feature name:value pairs from instances.
                    span(node.labeling.bestLabel)
//                    span(' ')
//                    span(node.labeling.bestValue)
//                    span(' ')
//                    span(node.labeling.numLocations())
                }
            }

            node2table model.root
        }
    }
}
