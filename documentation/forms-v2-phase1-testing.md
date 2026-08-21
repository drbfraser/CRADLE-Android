# Forms V2 API — testing

This document covers the manual API testing done for Phase 1 of the Forms V1 → V2 migration

---

## 1. Create a form template

**Request**
```
POST /forms/v2/templates/body
Content-Type: application/json

{
  "version": 1,
  "classification": {
    "name": { "english": "test" }
  },
  "questions": [
    {
      "order": 0,
      "questionType": "MULTIPLE_CHOICE",
      "questionText": { "english": "Select symptoms" },
      "required": true,
      "mcOptions": [
        { "stringId": "opt-headache", "translations": { "english": "Headache" } },
        { "stringId": "opt-fever", "translations": { "english": "Fever" } },
        { "stringId": "opt-nausea", "translations": { "english": "Nausea" } }
      ]
    },
    {
      "order": 1,
      "questionType": "MULTIPLE_SELECT",
      "questionText": { "english": "Select all that apply" },
      "required": false,
      "mcOptions": [
        { "stringId": "opt-yes", "translations": { "english": "Yes" } },
        { "stringId": "opt-no", "translations": { "english": "No" } }
      ]
    }
  ]
}
```

**Response** — `201 Created`
```json
{
  "id": "9814c567-21ab-453d-8699-b108658b1853",
  "formClassificationId": "9bb5b565-5759-48e3-8f7c-5f0d33873f50",
  "version": 1,
  "archived": false,
  "name": "test",
  "dateCreated": 1784048596
}
```

 `MULTIPLE_CHOICE`/`MULTIPLE_SELECT` was used to avoid a bug that was encountered
 ---

## 2. Get the template back (to grab question IDs)

**Request**
```
GET /forms/v2/templates/9814c567-21ab-453d-8699-b108658b1853
```

**Response** — `200 OK`
```json
{
  "id": "9814c567-21ab-453d-8699-b108658b1853",
  "version": 1,
  "archived": false,
  "classification": {
    "id": "9bb5b565-5759-48e3-8f7c-5f0d33873f50",
    "name": { "english": "Smoke Test Classification" },
    "nameStringId": "48490282-5128-4a5b-8c1b-6422fdd40c18"
  },
  "dateCreated": 1784048596,
  "questions": [
    {
      "id": "0665db94-c7da-4a94-9ac8-0fcc7e1b1b16",
      "order": 0,
      "questionType": "MULTIPLE_CHOICE",
      "questionText": { "english": "Select symptoms" },
      "mcOptions": [
        { "stringId": "opt-headache", "translations": { "english": "Headache" } },
        { "stringId": "opt-fever", "translations": { "english": "Fever" } },
        { "stringId": "opt-nausea", "translations": { "english": "Nausea" } }
      ],
      "required": true
    },
    {
      "id": "b382eaa1-65ba-4b8a-9354-4693c7c6533e",
      "order": 1,
      "questionType": "MULTIPLE_SELECT",
      "questionText": { "english": "Select all that apply" },
      "mcOptions": [
        { "stringId": "opt-yes", "translations": { "english": "Yes" } },
        { "stringId": "opt-no", "translations": { "english": "No" } }
      ],
      "required": false
    }
  ]
}
```

---

## 3. Create a form submission

**Request**
```
POST /forms/v2/submissions
Content-Type: application/json

{
  "formTemplateId": "9814c567-21ab-453d-8699-b108658b1853",
  "patientId": "1",
  "userId": 1,
  "answers": [
    { "questionId": "0665db94-c7da-4a94-9ac8-0fcc7e1b1b16", "answer": { "mcIdArray": [0] } },
    { "questionId": "b382eaa1-65ba-4b8a-9354-4693c7c6533e", "answer": { "mcIdArray": [0, 1] } }
  ]
}
```

**Response** — `201 Created`
```json
{
  "id": "2e9e4483-faa8-49be-919c-01dd98998742",
  "formTemplateId": "9814c567-21ab-453d-8699-b108658b1853",
  "patientId": "1",
  "userId": 1,
  "dateSubmitted": 1784048647,
  "lastEdited": 1784048647,
  "lang": "English",
  "archived": false
}
```

A bug was also encountered here

---

## 4. Get the submission back

**Request**
```
GET /forms/v2/submissions/2e9e4483-faa8-49be-919c-01dd98998742
```

**Response** — `200 OK`
```json
{
  "id": "2e9e4483-faa8-49be-919c-01dd98998742",
  "formTemplateId": "9814c567-21ab-453d-8699-b108658b1853",
  "patientId": "1",
  "userId": 1,
  "dateSubmitted": 1784048647,
  "lastEdited": 1784048647,
  "lang": "English",
  "archived": false,
  "answers": [
    {
      "id": "e85362ed-d869-4399-a622-552c224e9cea",
      "questionId": "0665db94-c7da-4a94-9ac8-0fcc7e1b1b16",
      "questionText": "Select symptoms",
      "questionType": "MULTIPLE_CHOICE",
      "mcOptions": ["Headache", "Fever", "Nausea"],
      "answer": { "mcIdArray": [0], "comment": null },
      "order": 0
    },
    {
      "id": "9bf1de9c-9f55-47d3-940d-ec7f8631589a",
      "questionId": "b382eaa1-65ba-4b8a-9354-4693c7c6533e",
      "questionText": "Select all that apply",
      "questionType": "MULTIPLE_SELECT",
      "mcOptions": ["Yes", "No"],
      "answer": { "mcIdArray": [0, 1], "comment": null },
      "order": 1
    }
  ]
}
```

---

## 5. Patch (partially update) the submission

Changed the answer to "Select symptoms" from Headache (index 0) to Nausea (index 2), leaving the other answer untouched.

**Request**
```
PATCH /forms/v2/submissions/2e9e4483-faa8-49be-919c-01dd98998742
Content-Type: application/json

{
  "answers": [
    {
      "id": "e85362ed-d869-4399-a622-552c224e9cea",
      "questionId": "0665db94-c7da-4a94-9ac8-0fcc7e1b1b16",
      "answer": { "mcIdArray": [2] }
    }
  ]
}
```

**Response** — `200 OK`
```json
{
  "id": "2e9e4483-faa8-49be-919c-01dd98998742",
  "formTemplateId": "9814c567-21ab-453d-8699-b108658b1853",
  "patientId": "1",
  "userId": 1,
  "dateSubmitted": 1784048647,
  "lastEdited": 1784048668,
  "lang": "English",
  "archived": false
}
```

Result: Worked as expected — `lastEdited` timestamp updated.

---

## 6. Get the submission one more time, to confirm the patch actually saved

**Request**
```
GET /forms/v2/submissions/2e9e4483-faa8-49be-919c-01dd98998742
```

**Response** — `200 OK` (relevant part only)
```json
{
  "answers": [
    {
      "id": "e85362ed-d869-4399-a622-552c224e9cea",
      "questionId": "0665db94-c7da-4a94-9ac8-0fcc7e1b1b16",
      "mcOptions": ["Headache", "Fever", "Nausea"],
      "answer": { "mcIdArray": [2], "comment": null }
    },
    {
      "id": "9bf1de9c-9f55-47d3-940d-ec7f8631589a",
      "questionId": "b382eaa1-65ba-4b8a-9354-4693c7c6533e",
      "mcOptions": ["Yes", "No"],
      "answer": { "mcIdArray": [0, 1], "comment": null }
    }
  ]
}
```

Result: Confirmed. "Select symptoms" now shows `mcIdArray: [2]` (Nausea), and the other answer ("Select all that apply") was untouched by the patch. Partial update worked correctly.

---

## 7. List all templates

**Request**
```
GET /forms/v2/templates
Authorization: Bearer <accessToken>
```

**Response** — `200 OK` (list of shallow templates, wrapped in a `templates` array)

---

## 8. Classification summary

**Request**
```
GET /forms/v2/classifications/summary
Authorization: Bearer <accessToken>
```

**Response** — `200 OK` (array of full templates, one per classification)

---
## Bugs Found

### Bug #1: Creating a template crashes if a question has no `mcOptions`

If you try to create a template where a question is not a multiple-choice/multiple-select
question i.e it has no `mcOptions` field at all, e.g. a plain text question, the request fails
with a `500 Internal Server Error` instead of creating the template.

This is because the backend expects a list and when we try to iterate over it without actually having the list, we get an error

### Bug #2: `userId` is actually required, even though code says it is not

The code suggests that `userId` on a new submission is optional and should default to
whoever is logged in (from the auth token). In practice, leaving it out gives a
`422 Unprocessable Entity` error saying the field is required. Hence the code will need to be changed to accomodate this.