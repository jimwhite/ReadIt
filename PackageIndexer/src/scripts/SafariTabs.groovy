package scripts

@Grab(group='commons-configuration', module='commons-configuration', version='1.9')

def _x
println System.getProperty('user.home')
def session_file = new File("${System.getProperty('user.home')}/Library/Safari/LastSession.plist")
//def session_file = new File("${System.getProperty('user.home')}/LastSession.plist")
println session_file.path
def plist = new org.apache.commons.configuration.plist.XMLPropertyListConfiguration(session_file)

//println plist
//
//println (plist.rootNode.children.size())
//println (plist.rootNode.getChildren('SessionWindows').size())
//
//println plist.rootNode.getAttributeCount() ; println plist.rootNode.getChildrenCount()

plist.rootNode.getChildren('SessionWindows').each { windows ->
    windows.value.eachWithIndex { window, i ->
//        println window.getKeys('') as List
        def tabs = window.getProperty('TabStates')
        println "${windows.name} #$i has ${tabs.size()} tabs"
        tabs.each { tab ->
            println tab.getProperty('TabURL')
        }
    }
}
//plist.rootNode.getChildren('SessionWindows').each { println it.getChildren().size() }
//plist.rootNode.getChildren('SessionWindows').each { println it.getChildren('TabStates').size() }
//plist.rootNode.getChildren('SessionWindows').each { it.getChildren('TabStates').each { it.getAttributes('TabURL').each { println it.value }} }