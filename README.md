# teslate-android

Teslate is a temporary mirroring solution for Tesla vehicles
[until Tesla supports mirroring natively](http://www.teslarati.com/tesla-sdk-iphone-android-app-mirroring/). teslate-android is the
Android app that enables this mirroring solution.

# Installation



# Data usage

The in-car browser [will not connect to LAN resources]
(https://teslamotorsclub.com/tmc/threads/successful-connection-on-the-model-s-internal-ethernet-network.28185/page-19), so mirroring must
be done by bouncing off WAN via [teslate-server](https://github.com/AsturaPhoenix/teslate-server).

At present, Teslate may use about 40 MB per hour while mirroring actively changing screens such as navigation on a map. Monthly usage may
vary, but with liberal estimates of 4 hours on weekdays and 10 hours on weekends, this amounts to about 5 GB per month.
