@ignore
Feature: Reusable helper - creates a task for the user owning the given token

  Scenario:
    * url taskUrl
    Given path 'tasks'
    And header Authorization = 'Bearer ' + token
    And request { title: '#(title)', description: 'helper', status: '#(status)', dueDate: '2030-01-15' }
    When method post
    Then status 201
