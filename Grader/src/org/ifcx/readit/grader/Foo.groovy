package org.ifcx.readit.grader

def outFile = new File('/Users/jim/Downloads/q3acc.txt')


def sysText = outFile.text
// Turn into a list of non-empty lines.
def sysLines = sysText.readLines().collect { it.trim() }.findAll()
// Data lines for hw4 contain 8 columns and some are numbers.
def isDataLine = { it.split(/\s+/).size() == 3 && it.split(/[.\d]+/).size() > 2 }
def dataLines = sysLines.grep(isDataLine)
def dataCount = dataLines.size()

    def x = sysLines.first().split(/\s+/)
println x.size()
println x


def y = sysLines.first().split(/[.\d]+/)
println y.size()
println y
