import requests

url = "https://docs.newapi.pro/v1beta/models/string:generateContent/"
body = """{
  "contents": [
    {"text": "你好"}
  ],
  "generationConfig": {
    "responseModalities": [
      "string"
    ],
    "imageConfig": {
      "aspectRatio": "string",
      "imageSize": "string"
    }
  }
}"""
response = requests.request("POST", url, data = body, headers = {
  "Content-Type": "application/json", 
  "Authorization": "Bearer sk-MgVuquDqVte3C4PlsXoKm7wxdhcnYV0IsWYtTX5NEJbdEGQ2"
})

print(response.text)