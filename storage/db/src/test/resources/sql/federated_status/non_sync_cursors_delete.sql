delete
from node_event_cursor
where operator_id = 't1-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/t1-repo-cursor'
  and operator_id = 't2-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/t2-repo-cursor'
  and operator_id = 't3-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/t3-repo-cursor'
  and operator_id = 't4-repo-federated_replicationhttp://docker.for.mac.localhost:10102/artifactory/t4-repo-cursor'
  and operator_id = 'non-federated-repohttp://docker.for.mac.localhost:10102/artifactory/non-federated-repo-cursor';
