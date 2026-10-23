@ignore
Feature: Reusable helper - registers a brand new user and logs in

  Scenario:
    * def email = 'user-' + java.util.UUID.randomUUID() + '@example.com'
    * def password = 'S3cret-pass!'

    Given url authUrl
    And path 'auth', 'register'
    And request { email: '#(email)', password: '#(password)' }
    When method post
    Then status 201
    * def userId = response.id

    Given url authUrl
    And path 'auth', 'login'
    And request { email: '#(email)', password: '#(password)' }
    When method post
    Then status 200
    * def token = response.accessToken
