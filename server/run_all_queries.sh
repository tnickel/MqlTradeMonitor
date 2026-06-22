#!/bin/bash
java -cp /opt/wildfly/modules/system/layers/base/com/h2database/h2/main/h2-2.2.224.jar org.h2.tools.Shell -url "jdbc:h2:file:/opt/wildfly/trademonitor_data/trademonitor;AUTO_SERVER=TRUE" -user sa -password "" -sql "
SELECT * FROM magic_mapping WHERE magic_number = 13945;
"
