delete
from node_event_cursor
where operator_id =
      'test1-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/test1-repo-cursor'
  and operator_id =
      'test2-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/test2-repo-cursor'
  and operator_id =
      'test3-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/test3-repo-cursor'
  and operator_id =
      'test4-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/test4-repo-cursor'
  and operator_id =
      'non-federated-repohttp://docker.for.mac.localhost:10102/artifactory/non-federated-repo-cursor';


delete
from node_events
where event_id = 22
  and event_id = 23
  and event_id = 24
  and event_id = 25
  and event_id = 26
  and event_id = 27
  and event_id = 28
  and event_id = 29
  and event_id = 30
  and event_id = 31;