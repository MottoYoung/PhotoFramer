# Task Request
The user has uploaded an image. Please apply Foreground Framing.
Only use this technique when there is already a real foreground object or frame candidate in the scene.
Never fabricate leaves, windows, poles, blur blobs, or fake frame edges.
If no real foreground/frame candidate exists, return is_applicable=false.
Reject this technique if the foreground/frame effect would be weak, ambiguous, or barely stronger than the original framing.
When applicable, create a clearly layered composition with an obvious framing element.
