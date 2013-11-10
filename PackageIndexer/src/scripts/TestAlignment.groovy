package scripts

import gate.Document
import gate.FeatureMap
import gate.Gate
import gate.Factory
import gate.Utils
//import gate.groovy.GateGroovyMethods

Gate.init()

File alignmentHome = new File(Gate.getPluginsHome(),"Alignment")
Gate.getCreoleRegister().addDirectory(alignmentHome.toURL());

println Gate.creoleRegister.getPublicLrTypes()

println Gate.creoleRegister.getLrInstances()

// use (Utils, GateGroovyMethods) {
use (Utils) {
    def aDocument = Factory.createResource("gate.compound.impl.CompoundDocumentImpl", [:].toFeatureMap())
    println aDocument
}


new File("foo").withPrintWriter { it.println() }