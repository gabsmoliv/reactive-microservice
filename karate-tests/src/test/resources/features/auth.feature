Feature: Registration and login

  Background:
    * url authUrl
    * def email = 'user-' + java.util.UUID.randomUUID() + '@example.com'
    * def password = 'S3cret-pass!'

  Scenario: Register a user, then login and receive an RS256 JWT
    Given path 'auth', 'register'
    And request { email: '#(email)', password: '#(password)' }
    When method post
    Then status 201
    And match response == { id: '#uuid', email: '#(email)', createdAt: '#string' }
    And match response contains { id: '#notnull' }
    And match karate.keysOf(response) !contains 'passwordHash'

    Given path 'auth', 'login'
    And request { email: '#(email)', password: '#(password)' }
    When method post
    Then status 200
    And match response == { accessToken: '#string', tokenType: 'Bearer', expiresIn: '#number' }

    # JWT header must announce RS256 and the subject must be the registered user id
    * def parts = response.accessToken.split('.')
    * def decode = function(s){ return JSON.parse('' + new java.lang.String(java.util.Base64.getUrlDecoder().decode(s), 'UTF-8')) }
    * def jwtHeader = decode(parts[0])
    * def jwtClaims = decode(parts[1])
    And match jwtHeader.alg == 'RS256'
    And match jwtClaims.sub == '#uuid'
    And match jwtClaims.email == email

  Scenario: Email is case-insensitive
    Given path 'auth', 'register'
    And request { email: '#(email)', password: '#(password)' }
    When method post
    Then status 201

    Given path 'auth', 'login'
    And request { email: '#(email.toUpperCase())', password: '#(password)' }
    When method post
    Then status 200

  Scenario: Registering the same email twice returns 409
    Given path 'auth', 'register'
    And request { email: '#(email)', password: '#(password)' }
    When method post
    Then status 201

    Given path 'auth', 'register'
    And request { email: '#(email)', password: '#(password)' }
    When method post
    Then status 409

  Scenario Outline: Invalid registration payloads are rejected with 400 - <name>
    Given path 'auth', 'register'
    And request { email: '<email>', password: '<password>' }
    When method post
    Then status 400
    And match response.message == 'Validation failed'

    Examples:
      | name           | email         | password    |
      | invalid email  | not-an-email  | S3cret-pass |
      | short password | a@example.com | short       |

  Scenario: Wrong password returns 401
    Given path 'auth', 'register'
    And request { email: '#(email)', password: '#(password)' }
    When method post
    Then status 201

    Given path 'auth', 'login'
    And request { email: '#(email)', password: 'wrong-password' }
    When method post
    Then status 401

  Scenario: Unknown user returns 401 with the same message as a wrong password
    Given path 'auth', 'login'
    And request { email: '#(email)', password: 'whatever-123' }
    When method post
    Then status 401
    And match response.message == 'Invalid email or password'

  Scenario: JWKS endpoint exposes only public key material
    Given path '.well-known', 'jwks.json'
    When method get
    Then status 200
    And match response.keys[0].kty == 'RSA'
    And match response.keys[0] contains { n: '#string', e: '#string' }
    And match response.keys[0] !contains { d: '#notnull' }
