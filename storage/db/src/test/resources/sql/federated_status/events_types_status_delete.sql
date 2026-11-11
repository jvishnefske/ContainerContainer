delete
from node_event_cursor
where operator_id =
      'fed1-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/fed1-cursor'
  and operator_id =
      'fed1-federated_replicationhttp://docker.for.mac.localhost:10103/artifactory/fed1-cursor'
  and operator_id =
      'fed1-federated_replicationhttp://docker.for.mac.localhost:10104/artifactory/fed1-cursor'
  and operator_id =
      'out-of-sync-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/out-of-sync-cursor'
  and operator_id =
      'non-federated-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/non-federated-cursor'
  and operator_id =
      'hint-not-null-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/hint-not-null-cursor'
  and operator_id =
      'no-match-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/no-match-cursor'
  and operator_id =
      'events-after-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/events-after-cursor'
  and operator_id =
      'out-of-sync-federated_replicationhttp://docker.for.mac.localhost:10103/artifactory/out-of-sync-cursor';


delete
from node_events
where event_id = 1
  and event_id = 2
  and event_id = 3
  and event_id = 4
  and event_id = 5
  and event_id = 6
  and event_id = 7
  and event_id = 8
  and event_id = 9
  and event_id = 10
  and event_id = 11
  and event_id = 12
  and event_id = 13;
