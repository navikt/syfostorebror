package no.nav.syfo.metrics

import io.prometheus.client.Counter

const val METRICS_NS = "syfostorebror"

val SM_OVERVAKET: Counter = Counter.build()
        .namespace(METRICS_NS)
        .name("sykmelding_overvaket_count")
        .help("Antall sykmeldinger talt og prosessert av syfostorebror")
        .register()

