🔨 Namma-Kelsa"Dignity-First" Labour Marketplace for Small Towns Industry Internship Project: Android App Development using GenAI
PRD: Project #53 · MindMatrix Internship
Institution: Adichunchanagiri Institute of Technology (AIT), Chikkamagaluru
Author: Gouthami T | USN: 4AI22IS018 | Dept. of IS & E
Guides: Mrs. Deepashri K S (Internal) · Tirumal Mutalikdesai (External, MindMatrix)


📌 Project Overview (PRD Section 1)
         Namma-Kelsa (meaning 'Our Work' in Kannada) is an Android application designed to digitally empower daily-wage workers—painters, plumbers, electricians, and gardeners—in small towns. By creating a direct, trust-based connection between skilled workers and homeowners, the app eliminates middleman commissions and builds a professional digital identity for local laborers.
         
GenAI Integration (PRD Section 5)
F-08: Skill Description Generator: Uses Gemini Vision to analyze uploaded work photos and auto-generate professional descriptions, ensuring high-quality profiles without requiring workers to type.
F-09: Natural Language Search: Allows customers to type queries like "fix a broken switch" or "paint my fence," which Gemini Pro resolves into specific skill categories.
F-10: Skill Badge Suggestion: Analyzes a worker's primary skill to suggest relevant sub-skills (e.g., suggesting "Waterproofing" for a Painter).


🏗️ Technical Architecture (PRD Section 8)
      The app follows the MVVM (Model-View-ViewModel) architecture to ensure a clean separation of concerns and scalability.
Model: Firestore Data Models (Worker.kt, Customer.kt)
View: Material Design 3 Activities and XML layouts
ViewModel: Logic for handling UI states and repository calls
Repository: FirebaseRepository.kt for centralized Firestore and Storage operations


Layer                                  Technology            
Platform	                              Android API 24+ (Nougat)
Language	                              Kotlin
Database	                              Firebase Firestore (Real-time NoSQL)
Storage	                              Firebase Storage (Portfolio Images)
Auth	                                 Firebase Authentication (Phone OTP & Email)
AI Engine	                           Google Gemini API (gemini-1.5-flash)
Location	                              FusedLocationProvider (Haversine Formula for < 2km search)



ID        Feature                                              Status
F-01      Dual-Role Auth (Worker/Customer)                     ✅
F-02      "Profile Setup (Skills, Rate, Profile Photo)"        ✅
F-04      Real-time Availability Toggle                        ✅
F-05      Location-based Search (< 2km radius)                 ✅
F-07      Direct Native Dialer Integration                     ✅
F-08      AI Photo Description (Gemini Vision)                 ✅
F-11      Offline Persistence (Firestore Persistence)          ✅
F-12      Automatic Midnight Availability Reset                ✅



🔐 Security & Privacy (PRD Section 6)
Firestore Rules: All data access is protected by authenticated UID checks.
API Security: The Gemini API Key is stored in local.properties and is never committed to GitHub.
Privacy: Worker contact details are only revealed when a customer performs an intentional "Call"    action.


📽️ Demo Execution (PRD Section 9)
Worker Flow: Register → AI-assisted profile creation → Upload work samples → Toggle "Available" status.
Customer Flow: Natural Language Search → View nearby workers (sorted by distance) → View AI-generated portfolio → Tap to Call.

Namma-Kelsa — Developed for the empowerment and dignity of local laborers.
