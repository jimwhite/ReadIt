package scripts

// Simple example from GATE Manual works dandy for tagging lines.

//matcher = content =~ /(?m)^.*$/
matcher = content =~ scriptParams.regex

//println content.size()
//println scriptParams.type

while(matcher.find()) {
//    println "${matcher.start()} ${matcher.end()}"
    outputAS.add(matcher.start(), matcher.end(), scriptParams.type, Factory.newFeatureMap())
}

//println "Done"
