package org.ifcx.readit.index

import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode
class Filler
{
    String label

    boolean filter(Answer answer) { true }

    Filler(label)
    {
        this.label = label
    }
}
