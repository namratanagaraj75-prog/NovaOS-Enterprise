@echo off
echo Staging modified files...
git add firestore.rules novaos/frontend/vercel.json novaos/backend/src/main/java/com/novaos/api/service/HiringDecisionPassportService.java novaos/backend/src/main/java/com/novaos/api/service/HiringRequestService.java novaos/frontend/src/pages/DecisionPassport.tsx novaos/frontend/src/pages/HiringRequestDetails.tsx

echo Committing changes...
git commit -m "Fix route refresh 404, Firestore rules, and real-time approval synchronization"

echo Pushing to GitHub...
git push origin main

echo Done!
