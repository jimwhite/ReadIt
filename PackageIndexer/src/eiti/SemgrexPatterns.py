#!/usr/local/bin/python2.6

# Thibaut Labarre (tlabarre)

'''
The basic idea is to compose a query that includes keywords or phrases for each of the slots included in the query.
The name of the entity naturally gets highest weight, but the addition of the slot-related terms will explode the number of matches and so you'll need to learn all about how that weighting/ranking stuff works.
The query step should read the query XML file, query Lucene, then write a file for each <query> element (using the id for the name) with a line for each file to be processed by the next stage.
'''
# python2.6 query2documents.py /corpora/LDC/LDC10E18/data/2010_training_entity_query_list.xml output/queries

# import modules
import re
import sys
import os
import operator
import math
import bisect
import subprocess
import xml.sax.handler

slots = ['PER_ALTERNATE_NAMES',
'PER_DATE_OF_BIRTH',
'PER_AGE',
'PER_COUNTRY_OF_BIRTH',
'PER_STATEORPROVINCE_OF_BIRTH',
'PER_CITY_OF_BIRTH',
'PER_ORIGIN',
'PER_DATE_OF_DEATH',
'PER_COUNTRY_OF_DEATH',
'PER_STATEORPROVINCE_OF_DEATH',
'PER_CITY_OF_DEATH',
'PER_CAUSE_OF_DEATH',
'PER_COUNTRIES_OF_RESIDENCE',
'PER_STATEORPROVINCES_OF_RESIDENCE',
'PER_CITIES_OF_RESIDENCE',
'PER_SCHOOLS_ATTENDED',
'PER_TITLE',
'PER_MEMBER_OF',
'PER_EMPLOYEE_OF',
'PER_RELIGION',
'PER_SPOUSE',
'PER_CHILDREN',
'PER_PARENTS',
'PER_SIBLINGS',
'PER_OTHER_FAMILY',
'PER_CHARGES']

# pattern functions
def family_pattern(lemma,invert=False):
    if invert:
        return '{lemma:' + lemma + '} >poss {ner:PERSON}=value & >/dep|appos/ {features:mention}=entity'
    else:
        return '{lemma:' + lemma + '} >poss {features:mention}=entity & >/dep|appos/ {ner:PERSON}=value'


def appos_pattern(ner):
    return [ '{ner:' + ner + '}=value </nn.*|appos/ {features:/mention.*/}=entity',
        '{} >> {ner:' + ner + '}=value & >> {features:/mention.*/}=entity',
        '{features:/mention.*/}=entity >/nn.*|appos/ {ner:' + ner + '}=value',
        '{ner:' + ner + '}=value >appos {tag:NNP}=entity',
        '{} >conj_and {features:/mention.*/}=entity & >conj_and {ner:' + ner + '}=value',
        '{} >> {features:/mention.*/}=entity & >appos {ner:' + ner + '}=value',
        '{} >appos {features:/mention.*/}=entity & >dep {ner:' + ner + '}=value']


# Patterns used multiple times
location_of_residence_patterns = [ '{features:mention}=entity >> ({ner:/COUNTRY|LOCATION/}=value <prep_to {lemma:move})',
                                   '{} > {tag:/PRP.*/;features:mention}=entity & > {ner:/COUNTRY|LOCATION/}=value',
                                   '{} >nsubj {features:mention}=entity & >prep_in {ner:/COUNTRY|LOCATION/}=value',
                                   '{ner:/COUNTRY|LOCATION/}=value <nn {features:mention}=entity',
                                   '{} > {features:/mention.*/}=entity & >nn {ner:/COUNTRY|LOCATION/}=value',
                                   '{} >advcl ({lemma:base} >/nsubj.*/ {features:mention}=entity) >prep_from ({} > {ner:/COUNTRY|LOCATION/}=value)']

location_of_birth_patterns = ['{features:mention}=entity >> ({ner:/COUNTRY|LOCATION/}=value <prep_in {lemma:bear})']

location_of_residence_patterns.extend(location_of_birth_patterns) # a city of birth is most probably a city of residence
location_of_residence_patterns.extend(appos_pattern('/COUNTRY|LOCATION/'))

location_of_death_patterns = ['{} >prep_to {lemma:death} & > ({} > {tag:/PRP.*/;features:mention}=entity & > {ner:/COUNTRY|LOCATION/}=value)']

parent = '/parent|parents|mother|father/'
children = '/child|children|daughter|son/'


# patterns per slot
patterns = {}

patterns['PER_ALTERNATE_NAMES'] = ['{lemma:change} >nsubj [{features:mention}=entity1|{ner:PERSON}=value2] & >dobj {lemma:name} & >/prep_from|prep_to/ [{features:mention}=entity2|{ner:PERSON}=value1]',
        '[{features:mention}=entity1|{ner:PERSON}=value2] >nn [{features:mention}=entity2|{tag:NNP}=value1]'  ]

patterns['PER_DATE_OF_BIRTH'] = ['({lemma:bear} >prep_on {ner:DATE}=value) < ({} >nsubj {ner:PERSON}=entity)']

patterns['PER_AGE'] = [ '{lemma:turn} > {ner:/NUMBER|DURATION/}=value & > {features:/mention.*/}=entity',
        '{} >> {features:/mention.*/}=entity & >appos {ner:/NUMBER|DURATION/}=value']

patterns['PER_COUNTRY_OF_BIRTH'] = location_of_birth_patterns


patterns['PER_STATEORPROVINCE_OF_BIRTH'] = location_of_birth_patterns

patterns['PER_CITY_OF_BIRTH'] = location_of_birth_patterns

patterns['PER_ORIGIN'] = ['{} >> {features:/mention.*/}=entity & >>appos ({} >amod {ner:MISC}=value)',
                    '{} >> {features:/mention.*/}=entity & >>appos ({} >amod {ner:MISC}=value)',
                    '{features:mention}=entity >nn {ner:MISC}=value']

patterns['PER_DATE_OF_DEATH'] = ['{}=entity >rcmod ({lemma:/kill|die/} >tmod {ner:DATE}=value)']

patterns['PER_COUNTRY_OF_DEATH'] = location_of_death_patterns
         
patterns['PER_STATEORPROVINCE_OF_DEATH'] = location_of_death_patterns

patterns['PER_CITY_OF_DEATH'] = location_of_death_patterns

patterns['PER_CAUSE_OF_DEATH'] = ['{lemma:death} >nn {}=value >prep_of {}=entity']



patterns['PER_COUNTRIES_OF_RESIDENCE'] = location_of_residence_patterns

patterns['PER_STATEORPROVINCES_OF_RESIDENCE'] = location_of_residence_patterns

patterns['PER_CITIES_OF_RESIDENCE'] = location_of_residence_patterns

patterns['PER_SCHOOLS_ATTENDED'] = ['{lemma:graduate} >nsubj {tag:/PRP.*/;features:mention}=entity & > {ner:ORGANIZATION}=value']

patterns['PER_TITLE'] = [ '{features:/mention.*/}=entity >rcmod ({tag:NN}=value >prep_of {ner:ORGANIZATION})',
        '{tag:NNP}=entity > ({lemma:run} >prep_for {lemma:/.*election/} & >prep_as {tag:NNP}=value)',
        '{features:mention}=entity >nn {tag:NNP}=value']
patterns['PER_TITLE'].extend(appos_pattern('/PERSON|TITLE/'))

patterns['PER_MEMBER_OF'] = ['{features:mention}=entity >appos ({lemma:president} >prep_of {ner:ORGANIZATION}=value)',
                             '{} >/nsubj.*/ {features:/mention.*/}=entity & >prep_at {tag:NNP}=value',
                             '{lemma:member} >/nsubj.*/ {tag:PRP}=entity & >prep_of {tag:NN}=value']
patterns['PER_MEMBER_OF'].extend(appos_pattern('/ORGANIZATION/'))

patterns['PER_EMPLOYEE_OF'] = ['{features:mention}=entity >rcmod ({tag:NN} >prep_of {ner:ORGANIZATION}=value)',
        '{lemma:head} >nsubj {features:mention}=entity & >prep_of {tag:/NN.*/}=value',
        '{ner:ORGANIZATION}=value </nn.*/ {features:mention}=entity',
        '{tag:NNP}=entity >appos ({} >prep_at {ner:ORGANIZATION}=value)',
        '{} >dep ({lemma:/find|hire|recruit|name|choose/} >nsubj {ner:ORGANIZATION}=value) & >nsubj {ner:PERSON}=entity',
        '{tag:NNP}=entity > ({lemma:run} >prep_for {lemma:/.*election/} & >prep_as {tag:NNP}=value)',
        '{} >> {ner:ORGANIZATION}=value &> ({lemma:eject} >dobj {features:mention}=entity)',
        '{} >>nsubj {features:mention}=entity & >> {lemma:serve}=value',
                               '{lemma:head}  >prep_of {tag:NN}=value >>/nn.*|appos/ {features:/mention.*/}=entity',
                               '{} >> {ner:ORGANIZATION}=value & >> {features:/mention.*/}=entity']
patterns['PER_EMPLOYEE_OF'].extend(appos_pattern('/ORGANIZATION|COMPANIES/'))

patterns['PER_RELIGION'] = ['{} > {features:mention}=entity & > {ner:MISC}=value']

patterns['PER_SPOUSE'] = [family_pattern('/husband|wife|spouse/',invert=True),
                            family_pattern('/husband|wife|spouse/'),
                          '{lemma:/husband|wife|spouse/} >poss {tag:/PRP.*/}=entity & >/dep|appos/ {ner:PERSON}=value']

patterns['PER_CHILDREN'] = [family_pattern(parent,invert=True),
                           family_pattern(children)]

patterns['PER_PARENTS'] = [family_pattern(parent),
                           family_pattern(children,invert=True)]

patterns['PER_SIBLINGS'] = [family_pattern('/sister|brother|sibling/')]

patterns['PER_OTHER_FAMILY'] = [family_pattern('/cousin|uncle|aunt|grandmother|grandfather|ancestor|relative/')]

patterns['PER_CHARGES'] = ['{lemma:plead} >/nsubj.*/ {features:mention}=entity & >prep_to ({lemma:charge}=value >/num|amod|nn/ {}=valueToken)',
                           '{lemma:charge} >/nsubj.*/ {tag:NNP}=entity & >prep_with ({}=value >/num|amod|nn/ {}=valueToken)',
                           '{features:mention}=entity < {lemma:conviction}=value']

for slot in slots:
    print ""
    print "// " + slot
    for pattern in patterns[slot]:
        print "rule(" + slot + ",'" + pattern + "')"
