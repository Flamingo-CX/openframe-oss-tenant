/**
 * GraphQL queries for logs
 */

export const GET_LOGS_QUERY = `
  query GetLogs($filter: LogFilterInput, $pagination: CursorPaginationInput, $search: String) {
    logs(filter: $filter, pagination: $pagination, search: $search) {
      edges {
        node {
          toolEventId
          eventType
          ingestDay
          toolType
          severity
          userId
          deviceId
          summary
          timestamp
          __typename
        }
        __typename
      }
      pageInfo {
        hasNextPage
        hasPreviousPage
        startCursor
        endCursor
        __typename
      }
      __typename
    }
  }
`

export const GET_LOG_DETAILS_QUERY = `
  query GetLogDetails($logId: ID!, $ingestDay: String!, $toolType: String!, $eventType: String!, $timestamp: Instant!) {
    logDetails(
      toolEventId: $logId
      ingestDay: $ingestDay
      toolType: $toolType
      eventType: $eventType
      timestamp: $timestamp
    ) {
      toolEventId
      eventType
      ingestDay
      toolType
      severity
      userId
      deviceId
      message
      timestamp
      details
      __typename
    }
  }
`