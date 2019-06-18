package no.nav.syfo.syfostorebror.metrics

import io.prometheus.client.Counter

const val METRICS_NS = "syfostorebror"

val AVVIST_SM_MOTTATT: Counter = Counter.build()
        .namespace(METRICS_NS)
        .name("avvist_sykmelding_mottatt_count")
        .help("Antall avviste sykmeldinger som er mottatt")
        .register()

