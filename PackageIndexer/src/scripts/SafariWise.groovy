package scripts

//def session_file = new File("${System.getProperty('user.home')}/Library/Safari/LastSession.plist")
def session_file = new File("${System.getProperty('user.home')}/LastSession.plist")

def plist = xmlwise.Plist.load(session_file)

plist.SessionWindows.eachWithIndex { window, i ->
    def tabs = window.TabStates
    println "Window #$i has ${tabs.size()} tabs"
    println tabs.TabURL
    tabs.each { println it.TabURL }
}
