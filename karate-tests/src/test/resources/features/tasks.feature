Feature: Task CRUD for an authenticated user

  Background:
    * def user = call read('classpath:features/new-user.feature')
    * def token = user.token
    * url taskUrl
    * configure headers = { Authorization: '#("Bearer " + token)' }
    * def payload = { title: 'Write README', description: 'Document the project', status: 'TODO', dueDate: '2030-01-15' }

  Scenario: Full lifecycle - create, read, update, delete
    Given path 'tasks'
    And request payload
    When method post
    Then status 201
    And match response ==
      """
      { id: '#uuid', title: 'Write README', description: 'Document the project', status: 'TODO',
        dueDate: '2030-01-15', createdAt: '#string', updatedAt: '#string' }
      """
    * def taskId = response.id

    Given path 'tasks', taskId
    When method get
    Then status 200
    And match response.title == 'Write README'

    Given path 'tasks', taskId
    And request { title: 'Write README v2', description: 'Updated', status: 'IN_PROGRESS', dueDate: '2030-02-01' }
    When method put
    Then status 200
    And match response contains { id: '#(taskId)', title: 'Write README v2', status: 'IN_PROGRESS', dueDate: '2030-02-01' }

    Given path 'tasks', taskId
    When method delete
    Then status 204

    Given path 'tasks', taskId
    When method get
    Then status 404

  Scenario: List returns only own tasks, newest first, and supports filter and pagination
    * def create = function(title, status){ return karate.call('classpath:features/create-task.feature', { title: title, status: status, token: token }).response }
    * def first = create('first', 'TODO')
    * def second = create('second', 'DONE')
    * def third = create('third', 'TODO')

    Given path 'tasks'
    When method get
    Then status 200
    And match response == '#[3]'
    And match response[*].title == ['third', 'second', 'first']

    Given path 'tasks'
    And param status = 'TODO'
    When method get
    Then status 200
    And match response[*].title == ['third', 'first']

    Given path 'tasks'
    And param page = 1
    And param size = 2
    When method get
    Then status 200
    And match response[*].title == ['first']

  Scenario Outline: Invalid task payloads are rejected with 400 - <name>
    Given path 'tasks'
    And request <body>
    When method post
    Then status 400

    Examples:
      | name           | body                                                                 |
      | blank title    | { title: '', status: 'TODO', dueDate: '2030-01-15' }                 |
      | missing status | { title: 'x', dueDate: '2030-01-15' }                                |
      | unknown status | { title: 'x', status: 'NOPE', dueDate: '2030-01-15' }                |
      | missing due    | { title: 'x', status: 'TODO' }                                       |
      | bad date       | { title: 'x', status: 'TODO', dueDate: 'tomorrow' }                  |

  Scenario: Malformed task id returns 400
    Given path 'tasks', 'not-a-uuid'
    When method get
    Then status 400

  Scenario: Page size above the limit returns 400
    Given path 'tasks'
    And param size = 1000
    When method get
    Then status 400
