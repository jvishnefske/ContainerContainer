# Documentation - https://jfrog-int.atlassian.net/wiki/spaces/RTD/pages/1042481277/Cache+Controller+Self+Check+Tool
#!/bin/bash
# default value for partitions is 1
partitions="${partitions:-1}"

# access_token, host, port and repo_path are mandatory params.
function check_args() {
    if [[ -z $access_token ]]; then
        usage
        exit_error "Missing parameter --access_token"
    elif [[ -z $host ]]; then
        usage
        exit_error "Missing parameter --host"
    elif [[ -z $port ]]; then
            usage
            exit_error "Missing parameter --port"
    elif [[ -z $repo_path ]]; then
        usage
        exit_error "Missing parameter --repo_path"
    fi
}

function check_miss() {
    if [[ $1 != "MISS" ]]; then
           exit_error "Expected 'MISS' for the first request, but received an unexpected response, Please contact Jfrog"
        else
           echo "Cache MISS confirmed as expected; proceeding with the next steps."
        fi
}

function check_hit() {
    for i in $(seq 1 $1);
    do
      echo "Retrying artifact download, expecting a cache HIT ...”"
      # make call, check status, exit if getting at least one HIT
      cmd=$(curl -s -o /dev/null -w "%{http_code}, %header{X-Cache-Status}" --header "Authorization: Bearer $access_token" $host:$port/artifactory/$repo_path)
        if [[ $cache_header == "HIT" ]]; then
           exit_success "Cache HIT confirmed"
        fi
    done
    exit_error "No cache HIT detected. There may be an issue with the configurations, Please contact Jfrog"
}

function exit_success() {
  printf "Script succeed: %s\n\n" "$1"
  exit 0
}

function exit_error() {
  printf "Script failed: %s\n\n" "$1"
  exit 1
}

check_status() {
  case "$1" in
    "302")
        exit_error "Received 302 from Artifactory: Please check configurations."
        ;;
    "401")
      exit_error "Received 401 from Artifactory: Authorization failed. Please verify the access_token."
      ;;
    "404")
      exit_error "Received 400 from Artifactory: Repository path not found. Please verify the URL, port, and repo_path."
      ;;
    "500")
      exit_error "Received 500 from Artifactory: An internal error occurred. Please check the Artifactory logs for details."
      ;;
    "503")
      exit_error "Received 503 from Artifactory: Please check configurations."
  esac
}

# read command line arguments
while [ $# -gt 0 ]; do
    if [[ $1 == "--"* ]]; then
        v="${1/--/}"
        declare "$v"="$2"
        shift
    fi
    shift
done

check_args
cmd=$(curl -s -o /dev/null -w "%{http_code}, %header{X-Cache-Status}" --header "Authorization: Bearer $access_token" $host:$port/artifactory/$repo_path)
resp=(${cmd//,/ })
status=${resp[0]}
cache_header=${resp[1]}
# First check returned status code
check_status $status
# Check we're getting MISS on first call
check_miss $cache_header
# Check we're getting at least one HIT for next (partitions count times) calls
check_hit $partitions $token