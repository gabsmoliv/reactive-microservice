Feature: Security - authentication and per-user isolation

  Background:
    * url taskUrl
    * def alice = call read('classpath:features/new-user.feature')
    * def bob = call read('classpath:features/new-user.feature')
    * def payload = { title: 'Alice secret', description: 'private', status: 'TODO', dueDate: '2030-01-15' }

  Scenario: Requests without a token are rejected with 401
    Given path 'tasks'
    When method get
    Then status 401

    Given path 'tasks'
    And request payload
    When method post
    Then status 401

  Scenario: Garbage and tampered tokens are rejected with 401
    Given path 'tasks'
    And header Authorization = 'Bearer not.a.jwt'
    When method get
    Then status 401

    # Valid structure, but the payload was swapped so the signature no longer matches
    * def parts = alice.token.split('.')
    * def forgedClaims = bob.token.split('.')[1]
    * def forged = parts[0] + '.' + forgedClaims + '.' + parts[2]
    Given path 'tasks'
    And header Authorization = 'Bearer ' + forged
    When method get
    Then status 401

  Scenario: A user cannot read, update or delete another user's task, and cannot see it listed
    Given path 'tasks'
    And header Authorization = 'Bearer ' + alice.token
    And request payload
    When method post
    Then status 201
    * def aliceTaskId = response.id

    # Bob gets 404 (not 403) so the existence of Alice's task is not revealed
    Given path 'tasks', aliceTaskId
    And header Authorization = 'Bearer ' + bob.token
    When method get
    Then status 404

    Given path 'tasks', aliceTaskId
    And header Authorization = 'Bearer ' + bob.token
    And request { title: 'hacked', status: 'DONE', dueDate: '2030-01-15' }
    When method put
    Then status 404

    Given path 'tasks', aliceTaskId
    And header Authorization = 'Bearer ' + bob.token
    When method delete
    Then status 404

    Given path 'tasks'
    And header Authorization = 'Bearer ' + bob.token
    When method get
    Then status 200
    And match response == []

    # Alice's task is untouched
    Given path 'tasks', aliceTaskId
    And header Authorization = 'Bearer ' + alice.token
    When method get
    Then status 200
    And match response.title == 'Alice secret'

  Scenario: The owner cannot be forged through the request body
    * def forgedBody = { title: 'x', status: 'TODO', dueDate: '2030-01-15', userId: '#(bob.userId)' }
    Given path 'tasks'
    And header Authorization = 'Bearer ' + alice.token
    And request forgedBody
    When method post
    Then status 201
    * def taskId = response.id

    Given path 'tasks', taskId
    And header Authorization = 'Bearer ' + bob.token
    When method get
    Then status 404

    Given path 'tasks', taskId
    And header Authorization = 'Bearer ' + alice.token
    When method get
    Then status 200
