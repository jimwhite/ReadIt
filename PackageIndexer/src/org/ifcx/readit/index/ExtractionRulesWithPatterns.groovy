package org.ifcx.readit.index

import static Model.*

class ExtractionRulesWithPatterns extends ExtractionRules
{
    ExtractionRulesWithPatterns()
    {
        //////////////////////
        //       PER        //
        //////////////////////

// PER_ALTERNATE_NAMES
        rule(PER_ALTERNATE_NAMES,'{lemma:change} >nsubj [{features:mention}=entity1|{ner:PERSON}=value2] & >dobj {lemma:name} & >/prep_from|prep_to/ [{features:mention}=entity2|{ner:PERSON}=value1]')
        rule(PER_ALTERNATE_NAMES,'[{features:mention}=entity1|{ner:PERSON}=value2] >nn [{features:mention}=entity2|{tag:NNP}=value1]')

// PER_DATE_OF_BIRTH
        rule(PER_DATE_OF_BIRTH,'({lemma:bear} >prep_on {ner:DATE}=value) < ({} >nsubj {ner:PERSON}=entity)')

// PER_AGE
        rule(PER_AGE,'{lemma:turn} > {ner:/NUMBER|DURATION/}=value & > {features:/mention.*/}=entity')
        rule(PER_AGE,'{} >> {features:/mention.*/}=entity & >appos {ner:/NUMBER|DURATION/}=value')

// PER_COUNTRY_OF_BIRTH
        rule(PER_COUNTRY_OF_BIRTH,'{features:mention}=entity >> ({ner:/COUNTRY|LOCATION/}=value <prep_in {lemma:bear})')

// PER_STATEORPROVINCE_OF_BIRTH
        rule(PER_STATEORPROVINCE_OF_BIRTH,'{features:mention}=entity >> ({ner:/COUNTRY|LOCATION/}=value <prep_in {lemma:bear})')

// PER_CITY_OF_BIRTH
        rule(PER_CITY_OF_BIRTH,'{features:mention}=entity >> ({ner:/COUNTRY|LOCATION/}=value <prep_in {lemma:bear})')

// PER_ORIGIN
        rule(PER_ORIGIN,'{} >> {features:/mention.*/}=entity & >>appos ({} >amod {ner:MISC}=value)')
        rule(PER_ORIGIN,'{} >> {features:/mention.*/}=entity & >>appos ({} >amod {ner:MISC}=value)')
        rule(PER_ORIGIN,'{features:mention}=entity >nn {ner:MISC}=value')

// PER_DATE_OF_DEATH
        rule(PER_DATE_OF_DEATH,'{}=entity >rcmod ({lemma:/kill|die/} >tmod {ner:DATE}=value)')

// PER_COUNTRY_OF_DEATH
        rule(PER_COUNTRY_OF_DEATH,'{} >prep_to {lemma:death} & > ({} > {tag:/PRP.*/;features:mention}=entity & > {ner:/COUNTRY|LOCATION/}=value)')

// PER_STATEORPROVINCE_OF_DEATH
        rule(PER_STATEORPROVINCE_OF_DEATH,'{} >prep_to {lemma:death} & > ({} > {tag:/PRP.*/;features:mention}=entity & > {ner:/COUNTRY|LOCATION/}=value)')

// PER_CITY_OF_DEATH
        rule(PER_CITY_OF_DEATH,'{} >prep_to {lemma:death} & > ({} > {tag:/PRP.*/;features:mention}=entity & > {ner:/COUNTRY|LOCATION/}=value)')

// PER_CAUSE_OF_DEATH
        rule(PER_CAUSE_OF_DEATH,'{lemma:death} >nn {}=value >prep_of {}=entity')

// PER_COUNTRIES_OF_RESIDENCE
        rule(PER_COUNTRIES_OF_RESIDENCE,'{ner:/COUNTRY|LOCATION/}=value </nn.*|appos/ {features:/mention.*/}=entity')
        rule(PER_COUNTRIES_OF_RESIDENCE,'{} >> {ner:/COUNTRY|LOCATION/}=value & >> {features:/mention.*/}=entity')
        rule(PER_COUNTRIES_OF_RESIDENCE,'{features:/mention.*/}=entity >/nn.*|appos/ {ner:/COUNTRY|LOCATION/}=value')
        rule(PER_COUNTRIES_OF_RESIDENCE,'{ner:/COUNTRY|LOCATION/}=value >appos {tag:NNP}=entity')
        rule(PER_COUNTRIES_OF_RESIDENCE,'{} >conj_and {features:/mention.*/}=entity & >conj_and {ner:/COUNTRY|LOCATION/}=value')
        rule(PER_COUNTRIES_OF_RESIDENCE,'{} >> {features:/mention.*/}=entity & >appos {ner:/COUNTRY|LOCATION/}=value')
        rule(PER_COUNTRIES_OF_RESIDENCE,'{} >appos {features:/mention.*/}=entity & >dep {ner:/COUNTRY|LOCATION/}=value')

// PER_STATEORPROVINCES_OF_RESIDENCE
        rule(PER_STATEORPROVINCES_OF_RESIDENCE,'{ner:LOCATION}=value </nn.*|appos/ {features:/mention.*/}=entity')
        rule(PER_STATEORPROVINCES_OF_RESIDENCE,'{} >> {ner:LOCATION}=value & >> {features:/mention.*/}=entity')
        rule(PER_STATEORPROVINCES_OF_RESIDENCE,'{features:/mention.*/}=entity >/nn.*|appos/ {ner:LOCATION}=value')
        rule(PER_STATEORPROVINCES_OF_RESIDENCE,'{ner:LOCATION}=value >appos {tag:NNP}=entity')
        rule(PER_STATEORPROVINCES_OF_RESIDENCE,'{} >conj_and {features:/mention.*/}=entity & >conj_and {ner:LOCATION}=value')
        rule(PER_STATEORPROVINCES_OF_RESIDENCE,'{} >> {features:/mention.*/}=entity & >appos {ner:LOCATION}=value')
        rule(PER_STATEORPROVINCES_OF_RESIDENCE,'{} >appos {features:/mention.*/}=entity & >dep {ner:LOCATION}=value')

// PER_CITIES_OF_RESIDENCE
        rule(PER_CITIES_OF_RESIDENCE,'{ner:LOCATION}=value </nn.*|appos/ {features:/mention.*/}=entity')
        rule(PER_CITIES_OF_RESIDENCE,'{} >> {ner:LOCATION}=value & >> {features:/mention.*/}=entity')
        rule(PER_CITIES_OF_RESIDENCE,'{features:/mention.*/}=entity >/nn.*|appos/ {ner:LOCATION}=value')
        rule(PER_CITIES_OF_RESIDENCE,'{ner:LOCATION}=value >appos {tag:NNP}=entity')
        rule(PER_CITIES_OF_RESIDENCE,'{} >conj_and {features:/mention.*/}=entity & >conj_and {ner:LOCATION}=value')
        rule(PER_CITIES_OF_RESIDENCE,'{} >> {features:/mention.*/}=entity & >appos {ner:LOCATION}=value')
        rule(PER_CITIES_OF_RESIDENCE,'{} >appos {features:/mention.*/}=entity & >dep {ner:LOCATION}=value')

// PER_SCHOOLS_ATTENDED
        rule(PER_SCHOOLS_ATTENDED,'{lemma:graduate} >nsubj {tag:/PRP.*/;features:mention}=entity & > {ner:ORGANIZATION}=value')

// PER_TITLE
        rule(PER_TITLE,'{features:/mention.*/}=entity >rcmod ({tag:NN}=value >prep_of {ner:ORGANIZATION})')
        rule(PER_TITLE,'{tag:NNP}=entity > ({lemma:run} >prep_for {lemma:/.*election/} & >prep_as {tag:NNP}=value)')
        rule(PER_TITLE,'{features:mention}=entity >nn {tag:NNP}=value')
        rule(PER_TITLE,'{ner:/PERSON|TITLE/}=value </nn.*|appos/ {features:/mention.*/}=entity')
        rule(PER_TITLE,'{} >> {ner:/PERSON|TITLE/}=value & >> {features:/mention.*/}=entity')
        rule(PER_TITLE,'{features:/mention.*/}=entity >/nn.*|appos/ {ner:/PERSON|TITLE/}=value')
        rule(PER_TITLE,'{ner:/PERSON|TITLE/}=value >appos {tag:NNP}=entity')
        rule(PER_TITLE,'{} >conj_and {features:/mention.*/}=entity & >conj_and {ner:/PERSON|TITLE/}=value')
        rule(PER_TITLE,'{} >> {features:/mention.*/}=entity & >appos {ner:/PERSON|TITLE/}=value')
        rule(PER_TITLE,'{} >appos {features:/mention.*/}=entity & >dep {ner:/PERSON|TITLE/}=value')

// PER_MEMBER_OF
        rule(PER_MEMBER_OF,'{features:mention}=entity >appos ({lemma:president} >prep_of {ner:ORGANIZATION}=value)')
        rule(PER_MEMBER_OF,'{} >/nsubj.*/ {features:/mention.*/}=entity & >prep_at {tag:NNP}=value')
        rule(PER_MEMBER_OF,'{lemma:member} >/nsubj.*/ {tag:PRP}=entity & >prep_of {tag:NN}=value')
        rule(PER_MEMBER_OF,'{ner:/ORGANIZATION/}=value </nn.*|appos/ {features:/mention.*/}=entity')
        rule(PER_MEMBER_OF,'{} >> {ner:/ORGANIZATION/}=value & >> {features:/mention.*/}=entity')
        rule(PER_MEMBER_OF,'{features:/mention.*/}=entity >/nn.*|appos/ {ner:/ORGANIZATION/}=value')
        rule(PER_MEMBER_OF,'{ner:/ORGANIZATION/}=value >appos {tag:NNP}=entity')
        rule(PER_MEMBER_OF,'{} >conj_and {features:/mention.*/}=entity & >conj_and {ner:/ORGANIZATION/}=value')
        rule(PER_MEMBER_OF,'{} >> {features:/mention.*/}=entity & >appos {ner:/ORGANIZATION/}=value')
        rule(PER_MEMBER_OF,'{} >appos {features:/mention.*/}=entity & >dep {ner:/ORGANIZATION/}=value')

// PER_EMPLOYEE_OF
        rule(PER_EMPLOYEE_OF,'{features:mention}=entity >rcmod ({tag:NN} >prep_of {ner:ORGANIZATION}=value)')
        rule(PER_EMPLOYEE_OF,'{lemma:head} >nsubj {features:mention}=entity & >prep_of {tag:/NN.*/}=value')
        rule(PER_EMPLOYEE_OF,'{ner:ORGANIZATION}=value </nn.*/ {features:mention}=entity')
        rule(PER_EMPLOYEE_OF,'{tag:NNP}=entity >appos ({} >prep_at {ner:ORGANIZATION}=value)')
        rule(PER_EMPLOYEE_OF,'{} >dep ({lemma:/find|hire|recruit|name|choose/} >nsubj {ner:ORGANIZATION}=value) & >nsubj {ner:PERSON}=entity')
        rule(PER_EMPLOYEE_OF,'{tag:NNP}=entity > ({lemma:run} >prep_for {lemma:/.*election/} & >prep_as {tag:NNP}=value)')
        rule(PER_EMPLOYEE_OF,'{} >> {ner:ORGANIZATION}=value &> ({lemma:eject} >dobj {features:mention}=entity)')
        rule(PER_EMPLOYEE_OF,'{} >>nsubj {features:mention}=entity & >> {lemma:serve}=value')
        rule(PER_EMPLOYEE_OF,'{lemma:head}  >prep_of {tag:NN}=value >>/nn.*|appos/ {features:/mention.*/}=entity')
        rule(PER_EMPLOYEE_OF,'{} >> {ner:ORGANIZATION}=value & >> {features:/mention.*/}=entity')
        rule(PER_EMPLOYEE_OF,'{ner:/ORGANIZATION|COMPANIES/}=value </nn.*|appos/ {features:/mention.*/}=entity')
        rule(PER_EMPLOYEE_OF,'{} >> {ner:/ORGANIZATION|COMPANIES/}=value & >> {features:/mention.*/}=entity')
        rule(PER_EMPLOYEE_OF,'{features:/mention.*/}=entity >/nn.*|appos/ {ner:/ORGANIZATION|COMPANIES/}=value')
        rule(PER_EMPLOYEE_OF,'{ner:/ORGANIZATION|COMPANIES/}=value >appos {tag:NNP}=entity')
        rule(PER_EMPLOYEE_OF,'{} >conj_and {features:/mention.*/}=entity & >conj_and {ner:/ORGANIZATION|COMPANIES/}=value')
        rule(PER_EMPLOYEE_OF,'{} >> {features:/mention.*/}=entity & >appos {ner:/ORGANIZATION|COMPANIES/}=value')
        rule(PER_EMPLOYEE_OF,'{} >appos {features:/mention.*/}=entity & >dep {ner:/ORGANIZATION|COMPANIES/}=value')

// PER_RELIGION
        rule(PER_RELIGION,'{} > {features:mention}=entity & > {ner:MISC}=value')

// PER_SPOUSE
        rule(PER_SPOUSE,'{lemma:/husband|wife|spouse/} >poss {ner:PERSON}=value & >/dep|appos/ {features:mention}=entity')
        rule(PER_SPOUSE,'{lemma:/husband|wife|spouse/} >poss {features:mention}=entity & >/dep|appos/ {ner:PERSON}=value')
        rule(PER_SPOUSE,'{lemma:/husband|wife|spouse/} >poss {tag:/PRP.*/}=entity & >/dep|appos/ {ner:PERSON}=value')

// PER_CHILDREN
        rule(PER_CHILDREN,'{lemma:/parent|parents|mother|father/} >poss {ner:PERSON}=value & >/dep|appos/ {features:mention}=entity')
        rule(PER_CHILDREN,'{lemma:/child|children|daughter|son/} >poss {features:mention}=entity & >/dep|appos/ {ner:PERSON}=value')

// PER_PARENTS
        rule(PER_PARENTS,'{lemma:/parent|parents|mother|father/} >poss {features:mention}=entity & >/dep|appos/ {ner:PERSON}=value')
        rule(PER_PARENTS,'{lemma:/child|children|daughter|son/} >poss {ner:PERSON}=value & >/dep|appos/ {features:mention}=entity')

// PER_SIBLINGS
        rule(PER_SIBLINGS,'{lemma:/sister|brother|sibling/} >poss {features:mention}=entity & >/dep|appos/ {ner:PERSON}=value')

// PER_OTHER_FAMILY
        rule(PER_OTHER_FAMILY,'{lemma:/cousin|uncle|aunt|grandmother|grandfather|ancestor|relative/} >poss {features:mention}=entity & >/dep|appos/ {ner:PERSON}=value')

// PER_CHARGES
        rule(PER_CHARGES,'{lemma:plead} >/nsubj.*/ {features:mention}=entity & >prep_to ({lemma:charge}=value >/num|amod|nn/ {}=valueToken)')
        rule(PER_CHARGES,'{lemma:charge} >/nsubj.*/ {tag:NNP}=entity & >prep_with ({}=value >/num|amod|nn/ {}=valueToken)')
        rule(PER_CHARGES,'{features:mention}=entity < {lemma:conviction}=value')
        }
}
