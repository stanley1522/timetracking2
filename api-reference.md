# Enabling the Timeadmin API
The API provides a basic REST API to provide access to Timeadmin functions.

By default, th API is not enabled. It can enabled with the following configuration property:
```api.enable=true```


# Methods

## Authentication and Authorization

The methods in the API are available for authenticated users with CLIENT permission.
The user can be specified with the ```ext_id``` cookie by supplying a special authentication token.

There are two options to obtain such token:

- Sign in to Timeadmin, navigate to the *My Tokens* page and create a token, or
- get it via an API call.

For the latter, the API contains the following methods:


### POST /api/login

Post the login credentials in JSON format and get an authentication token.

**Example:**

```
curl -X POST -H "Content-Type: application/json" -d '{"email":"default@tar.hu", "password":"abc123"}' <TIMEADMIN_URL>/api/login
```

**Example result:**

```
{
  "token":"4HKL5MTDME40IS20CG2R5B5ARBPWZXXJ"
}
```


### POST /api/logout

Invalidate an authentication token.

**Example:**

```
curl -X POST -H "Content-Type: application/json" -d '{"token":"4HKL5MTDME40IS20CG2R5B5ARBPWZXXJ"}' <TIMEADMIN_URL>/api/logout
```

**Example result:**

```
{
  "status":"OK"
}
```


## Manage Task items


### GET /api/tasks

Get all available tasks in Timeadmin.

**Example query:**

```
curl -H 'Cookie: ext_id=<AUTH_TOKEN>;' <TIMEADMIN_URL>/api/tasks
```

**Example result:**
```
[
  {
    "id":6,
    "taskName":"Create DB Schema",
    "projectName":"Awesome Project",
    "fullName":"Awesome Project-Create DB Schema",
    "color":{
      "red":3,
      "green":164,
      "blue":72,
      "alpha":1
    }
  },
  {
    "id":5,
    "taskName":"FB Login",
    "projectName":"Awesome Project",
    "fullName":"Awesome Project-FB Login",
    "color":{
      "red":8,
      "green":14,
      "blue":163,
      "alpha":1
    }
  },
  {
    "id":7,
    "taskName":"Landing Page",
    "projectName":"Awesome Project",
    "fullName":"Awesome Project-Landing Page",
    "color":{
      "red":193,
      "green":213,
      "blue":6,
      "alpha":1
    }
  },
  {
    "id":4,
    "taskName":"More Support",
    "projectName":"Another Awesome Project",
    "fullName":"Another Awesome Project-More Support",
    "color":{
      "red":240,
      "green":111,
      "blue":223,
      "alpha":1
    }
  },
  {
    "id":3,
    "taskName":"Support",
    "projectName":"Another Awesome Project",
    "fullName":"Another Awesome Project-Support",
    "color":{
      "red":98,
      "green":201,
      "blue":233,
      "alpha":1
    }
  }
]
```


### GET /api/taskitems/[date-from]-[date-to]

List all task items of the user for the given date interval.
The dates are in **YYYYMMDD** format.

**Example query:**
```
curl -H 'Cookie: ext_id=<AUTH_TOKEN>;' <TIMEADMIN_URL>/api/taskitems/20170601-20180602
```

**Example result:**
It returns a dummy taskitem if there are no items found for the specified interval:

```
[
  {
    "id":-1,
    "taskId":0,
    "start":1496268000001,
    "duration":0,
    "user":1
  }
]
```

Or it returns the taskitems for that period:
```
[
  {
    "id":1,
    "taskId":3,
    "start":1506601655521,
    "duration":75210318,
    "user":1
  }
]
```


### POST /api/taskitems

Adds a new task item for the given user. The POST body has to contain the details of the new item in JSON format.

**Example:**
```
curl -X POST -H "Content-Type: application/json" -H 'Cookie: ext_id=<AUTH_TOKEN>;' -d '{"taskId":"2", "time":"1506601655521"}' <TIMEADMIN_URL>/api/taskitems
```

**Example result:**
```
{
  "status":"OK",
  "id":"<NEW-TASKITEM-ID>"
}
```


### PUT /api/taskitems/[id]

Modifies the specified item. The body of the request has to contain the details of the item in JSON format.

**Example:**
```
curl -X PUT -H "Content-Type: application/json" -H 'Cookie: ext_id=<AUTH_TOKEN>;' -d '{"taskId":"2", "time":"1506601655521"}' <TIMEADMIN_URL>/api/taskitems/123
```

**Example result:**
```
{
  "status":"OK"
}
```


### DELETE /api/taskitems/[id]

Deletes the specified item.

**Example:**
```
curl -X DELETE -H 'Cookie: ext_id=<AUTH_TOKEN>;' <TIMEADMIN_URL>/api/taskitems/123
```

**Example result:**
```
{
  "status":"OK"
}
```
